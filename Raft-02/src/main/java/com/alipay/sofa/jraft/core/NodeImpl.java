package com.alipay.sofa.jraft.core;

import com.alipay.sofa.jraft.*;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.conf.ConfigurationEntry;
import com.alipay.sofa.jraft.conf.ConfigurationManager;
import com.alipay.sofa.jraft.entity.*;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.option.*;
import com.alipay.sofa.jraft.rpc.RaftClientService;
import com.alipay.sofa.jraft.rpc.RaftServerService;
import com.alipay.sofa.jraft.rpc.RpcRequests;
import com.alipay.sofa.jraft.rpc.closure.RpcRequestClosure;
import com.alipay.sofa.jraft.rpc.closure.RpcResponseClosureAdapter;
import com.alipay.sofa.jraft.storage.LogManager;
import com.alipay.sofa.jraft.storage.LogStorage;
import com.alipay.sofa.jraft.storage.RaftMetaStorage;
import com.alipay.sofa.jraft.storage.impl.LogManagerImpl;
import com.alipay.sofa.jraft.util.*;
import com.alipay.sofa.jraft.util.concurrent.LongHeldDetectingReadWriteLock;
import com.alipay.sofa.jraft.util.timer.RaftTimerFactory;
import com.google.protobuf.Message;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.stream.Collectors;

/**
 * @author : 小何
 * @Description : 节点，最重要的类
 * @date : 2024-04-04 18:03
 */
public class NodeImpl implements Node, RaftServerService {
    private static final Logger logger = LoggerFactory.getLogger(NodeImpl.class);
    private static class NodeReadWriteLock extends LongHeldDetectingReadWriteLock {
        static final long MAX_BLOCKING_MS_TO_REPORT = SystemPropertyUtil.getLong(
                "jraft.node.detecting.lock.max_blocking_ms_to_report", -1
        );
        private final Node node;

        public NodeReadWriteLock(Node node) {
            super(MAX_BLOCKING_MS_TO_REPORT, TimeUnit.MILLISECONDS);
            this.node = node;
        }
        @Override
        public void report(final AcquireMode acquireMode, final Thread heldThread,
                           final Collection<Thread> queuedThreads, final long blockedNanos) {
            final long blockedMs = TimeUnit.NANOSECONDS.toMillis(blockedNanos);
            logger.warn(
                    "Raft-Node-Lock report: currentThread={}, acquireMode={}, heldThread={}, queuedThreads={}, blockedMs={}.",
                    Thread.currentThread(), acquireMode, heldThread, queuedThreads, blockedMs);
        }
    }

    /**
     * 配置上下文
     */
    private static class ConfigurationCtx {
        enum Stage {
            // 配置没有变更
            STAGE_NONE,
            // 当前节点正在追赶数据进度
            STAGE_CATCHING_UP,
            // 新旧配置的过度阶段
            STAGE_JOINT,
            // 配置变更完毕
            STAGE_STABLE
        }
        // 当前节点
        final NodeImpl node;
        // 节点的配置状态
        Stage stage;
        // 配置已经更改多少次了
        int nchanges;
        // 版本
        long version;
        List<PeerId> newPeers = new ArrayList<>();
        List<PeerId> oldPeers = new ArrayList<>();
        List<PeerId> addingPeers = new ArrayList<>();
        List<PeerId> newLearners = new ArrayList<>();
        List<PeerId> oldLearners = new ArrayList<>();
        Closure done;
        public ConfigurationCtx(final NodeImpl node) {
            super();
            this.node = node;
            this.stage = Stage.STAGE_NONE;
            this.version = 0;
            this.done = null;
        }
        void flush(final Configuration conf, final Configuration oldConf) {
            Requires.requireTrue(!isBusy(), "Flush when busy");
            this.newPeers = conf.listPeers();
            this.newLearners = conf.listLearners();
            if (oldConf == null || oldConf.isEmpty()) {
                this.stage = Stage.STAGE_STABLE;
                this.oldPeers = this.newPeers;
                this.oldLearners = this.newLearners;
            } else {
                this.stage = Stage.STAGE_JOINT;
                this.oldPeers = oldConf.listPeers();
                this.oldLearners = oldConf.listLearners();
            }
        }
        boolean isBusy() {
            return this.stage != Stage.STAGE_NONE;
        }

    }

    /**
     * 候选者发送的 正式投票请求 接收到响应时会执行的接口
     */
    private class OnRequestVoteRpcDone extends RpcResponseClosureAdapter<RpcRequests.RequestVoteResponse> {

        final long startMs;
        final PeerId peer;
        final long term;
        final NodeImpl node;
        RpcRequests.RequestVoteRequest request;
        public OnRequestVoteRpcDone(final PeerId peer, final long term, final NodeImpl node) {
            super();
            this.startMs = Utils.monotonicMs();
            this.peer = peer;
            this.term = term;
            this.node = node;
        }
        @Override
        public void run(Status status) {
            NodeImpl.this.metrics.recordLatency("request-vote", Utils.monotonicMs() - this.startMs);
            if (!status.isOk()) {
                logger.warn("Node {} RequestVote to {} error : {}.", this.node.getNodeId(), this.peer, status);
            } else {
                this.node.handleRequestVoteResponse(this.peer, this.term, getResponse());
            }
        }
    }

    /**
     * 候选者发送的 预投票请求 接收到响应时会执行的接口
     */
    private class OnPreVoteRpcDone extends RpcResponseClosureAdapter<RpcRequests.RequestVoteResponse> {
        final long startMs;
        final PeerId peer;
        final long term;
        RpcRequests.RequestVoteRequest request;
        public OnPreVoteRpcDone(final PeerId peer, final long term) {
            super();
            this.startMs = Utils.monotonicMs();
            this.peer = peer;
            this.term = term;
        }

        @Override
        public void run(Status status) {
            NodeImpl.this.metrics.recordLatency("pre-vote", Utils.monotonicMs() - this.startMs);
            if (!status.isOk()) {
                logger.warn("Node {} PreVote to {} error: {}.", getNodeId(), this.peer, status);
            } else {
                handlePreVoteResponse(this.peer, this.term, getResponse());
            }
        }
    }

    /**
     * Disruptor队列中的数据
     */
    private static class LogEntryAndClosure {
        LogEntry entry;
        Closure done;
        long expectedTerm;
        CountDownLatch shutdownLatch;
        public void reset() {
            this.entry = null;
            this.done = null;
            this.expectedTerm = 0;
            this.shutdownLatch = null;
        }
    }

    /**
     * 生产Event的工厂
     */
    private static class LogEntryAndClosureFactory implements EventFactory<LogEntryAndClosure> {
        @Override
        public LogEntryAndClosure newInstance() {
            return new LogEntryAndClosure();
        }
    }
    private class LogEntryAndClosureHandler implements EventHandler<LogEntryAndClosure> {
        /**
         * 批处理集合，容量为32
         */
        private final List<LogEntryAndClosure> tasks = new ArrayList<>(NodeImpl.this.raftOptions.getApplyBatch());
        private void reset() {
            for (final LogEntryAndClosure task : tasks) {
                task.reset();
            }
            this.tasks.clear();
        }
        @Override
        public void onEvent(LogEntryAndClosure event, final long sequence, final boolean endOfBatch) throws Exception {
            // 如果该节点要下线了，将剩余的LogEntry持久化
            if (event.shutdownLatch != null) {
                if (!this.tasks.isEmpty()) {
                    executeApplyingTasks(this.tasks);
                    reset();
                }
                final int num = GLOBAL_NUM_NODES.decrementAndGet();
                logger.info("The number of active nodes decrement to {}.", num);
                return;
            }
            // 将事件添加到集合中，集合达到一定数量时应用
            this.tasks.add(event);
            if (this.tasks.size() >= NodeImpl.this.raftOptions.getApplyBatch()) {
                executeApplyingTasks(this.tasks);
                reset();
            }
        }
    }

    /**
     * 业务层提交日志，Leader持久化本地后会向集群发送信息询问，在这里执行回调
     */
    class LeaderStableClosure extends LogManager.StableClosure {

        public LeaderStableClosure(final List<LogEntry> entries) {
            super(entries);
        }

        @Override
        public void run(final Status status) {
            if (status.isOk()) {
                System.out.println("日志写入数据库成功！！！！！！！！！！！！！");
            } else {
                logger.error("Node {} append [{}, {}] failed, status={}.", getNodeId(), this.firstLogIndex,
                        this.firstLogIndex + this.nEntries - 1, status);
            }
        }
    }
    /**
     * 定时器工厂，内含多个定时器
     */
    public final static RaftTimerFactory TIMER_FACTORY = JRaftUtils.raftTimerFactory();
    /**
     * 此进程启动了多少个节点
     */
    public static final AtomicInteger GLOBAL_NUM_NODES = new AtomicInteger(0);

    private final ReadWriteLock readWriteLock = new NodeReadWriteLock(this);
    protected final Lock writeLock = this.readWriteLock.writeLock();
    protected final Lock readLock = this.readWriteLock.readLock();
    /**
     * 当前节点的状态
     */
    private volatile State state;
    /**
     * 节点的当前任期
     */
    private long currTerm;
    /**
     * 上一次收到领导者心跳的时间
     */
    private volatile long lastLeaderTimestamp;
    /**
     * 当前节点的Leader节点
     */
    private PeerId leaderId = new PeerId();
    /**
     * 在此任期内，本节点投票给谁了
     */
    private PeerId votedId;
    /**
     * 正式投票计数器
     */
    private final Ballot voteCtx = new Ballot();
    /**
     * 预投票计数器
     */
    private final Ballot preVoteCtx = new Ballot();
    /**
     * 当前节点的配置条目，包含本次、上一次配置信息
     */
    private ConfigurationEntry conf;
    /**
     * 当前节点所属集群名称
     */
    private final String groupId;
    /**
     * 节点的配置信息
     */
    private NodeOptions options;
    private RaftOptions raftOptions;
    /**
     * 本节点的id
     */
    private final PeerId serverId;
    /**
     * 处理配置变更的上下文对象
     */
    private final ConfigurationCtx confCtx;
    /**
     * 日志存储器
     */
    private LogStorage logStorage;
    /**
     * 元数据存储器
     */
    private RaftMetaStorage raftMetaStorage;
    /**
     * 配置管理器，内含集群配置的更改的历史信息
     */
    private ConfigurationManager configManager;
    /**
     * 日志管理组件
     */
    private LogManager logManager;
    /**
     * 复制器组
     */
    private ReplicatorGroup replicatorGroup;
    /**
     * 发送消息的客户端
     */
    private RaftClientService rpcService;
    /**
     * 全局定时任务管理器
     */
    private Scheduler timerManager;
    /**
     * 选举超时定时器
     */
    private RepeatedTimer electionTimer;
    private NodeMetrics metrics;
    /**
     * 当前节点的 NodeId
     */
    private NodeId nodeId;
    /**
     * 提供各种服务的工厂，如创建日志存储器、元数据存储器、日志编码解码器工厂
     */
    private JRaftServiceFactory serviceFactory;
    private Disruptor<LogEntryAndClosure> applyDisruptor;
    private RingBuffer<LogEntryAndClosure> applyQueue;
    // ------------------------------------------------- 与业务层交互的方法 --------------------------------------
    public void apply(Task task) {
        LogEntry entry = new LogEntry();
        final EventTranslator<LogEntryAndClosure> translator = new EventTranslator<LogEntryAndClosure>() {
            @Override
            public void translateTo(LogEntryAndClosure event, long sequence) {
                event.done = task.getDone();
                event.entry = entry;
                event.expectedTerm = task.getExpectedTerm();
            }
        };

    }

    // --------------------------------------------------- 与集群交互的方法 -----------------------------------------
    public NodeImpl() {
        this(null, null);
    }
    public NodeImpl(final String groupId, final PeerId serverId) {
        super();
        if (groupId != null) {
            Utils.verifyGroupId(groupId);
        }
        this.groupId = groupId;
        this.serverId = serverId == null ? serverId.copy() : null;
        this.state = State.STATE_UNINITIALIZED;
        // 初始化 lastLeaderTimestamp 的值
        updateLastLeaderTimestamp(Utils.monotonicMs());
        this.currTerm = 0;
        // 初始化 配置信息上下文
        this.confCtx = new ConfigurationCtx(this);
        final int num = GLOBAL_NUM_NODES.incrementAndGet();
        logger.info("The number of active nodes increment to {}.", num);
    }

    @Override
    public boolean init(NodeOptions opts) {
        Requires.requireNonNull(opts, "Null node options");
        Requires.requireNonNull(opts.getRaftOptions(), "Null raft options");
        Requires.requireNonNull(opts.getServiceFactory(), "Null jraft service factory");
        this.serviceFactory = opts.getServiceFactory();
        this.options = opts;
        this.raftOptions = opts.getRaftOptions();
        this.metrics = new NodeMetrics(opts.isEnableMetrics());
        this.serverId.setPriority(opts.getElectionPriority());
        if (this.serverId.getIp().equals(Utils.IP_ANY)) {
            logger.error("Node can't started from IP_ANY");
            return false;
        }
        // 当前节点IP是否已经添加到节点管理器中了。RaftGroupService的start方法会将节点添加进去
        if (NodeManager.getInstance().serverExists(this.serverId.getEndpoint())) {
            logger.error("No RPC server attached to, did you forget to call addService?");
            return false;
        }
        if (this.options.getAppendEntriesExecutors() == null) {
            this.options.setAppendEntriesExecutors(Utils.getDefaultAppendEntriesExecutor());
        }
        // 全局定时任务管理器
        this.timerManager = TIMER_FACTORY.getRaftScheduler(this.options.isSharedTimerPool(), this.options.getTimerPoolSize(), "JRaft-Node-ScheduleThreadPool");
        final String suffix = getNodeId().toString();
        String name = "JRaft-ElectionTimer-" + suffix;
        // 超时选举，超过时间后开始进入选举流程
        this.electionTimer = new RepeatedTimer(name, this.options.getElectionTimeoutMs(), TIMER_FACTORY.getElectionTimer(this.options.isSharedElectionTimer(), name)) {
            @Override
            protected void onTrigger() {
                handleElectionTimeout();
            }

            @Override
            protected int adjustTimeout(int timeoutMs) {
                return randomTimeout(timeoutMs);
            }
        };
        this.configManager = new ConfigurationManager();
        if (!initLogStorage()) {
            logger.error("Node {} initLogStorage failed.",getNodeId());
            return false;
        }
        final Status st = this.logManager.checkConsistency();
        if (!st.isOk()){
            return false;
        }
        this.conf = new ConfigurationEntry();
        this.conf.setId(new LogId());
        this.conf.setConf(this.options.getInitialConf());
        if (!this.conf.isEmpty()) {
            Requires.requireTrue(this.conf.isValid(), "Invalid conf : %s", this.conf);
        } else {
            logger.info("Init node {} with empty conf.", this.serverId);
        }
        this.replicatorGroup = new ReplicatorGroupImpl();
        // 初始化当前节点的客户端
        this.rpcService = new DefaultRaftClientService(this.replicatorGroup, this.options.getAppendEntriesExecutors());
        final ReplicatorGroupOptions rgOpts = new ReplicatorGroupOptions();
        // 心跳时间 = 超时选举时间 / 10
        rgOpts.setHeartbeatTimeoutMs(heartbeatTimeout(this.options.getElectionTimeoutMs()));
        rgOpts.setElectionTimeoutMs(this.options.getElectionTimeoutMs());
        rgOpts.setLogManager(this.logManager);
        rgOpts.setNode(this);
        rgOpts.setRaftRpcClientService(this.rpcService);
        rgOpts.setRaftOptions(this.raftOptions);
        rgOpts.setTimerManager(this.timerManager);
        this.options.setMetricRegistry(this.metrics.getMetricRegistry());
        // 初始化客户端服务, 初始化复制器组
        if (!this.rpcService.init(this.options)) {
            return false;
        }
        this.replicatorGroup.init(new NodeId(this.groupId, this.serverId), rgOpts);
        this.state = State.STATE_FOLLOWER;
        // 一般都会走这里，进入选举流程
        if (!this.conf.isEmpty()) {
            stepDown(this.currTerm, false, new Status());
        }
        if (!NodeManager.getInstance().add(this)) {
            return false;
        }
        this.writeLock.lock();
        // 如果集群中只有当前一个节点, 选举自己，成为Leader
        if (this.conf.isStable() && this.conf.getConf().size() == 1 && this.conf.getConf().contains(this.serverId)) {
            electSelf();
        } else {
            this.writeLock.unlock();
        }
        return true;
    }

    @Override
    public void shutdown() {

    }

    private int heartbeatTimeout(final int electionTimeout) {
        return Math.max(electionTimeout / this.raftOptions.getElectionHeartbeatFactor(), 10);
    }
    /**
     * 身份降级
     * @param term 新term
     * @param wakeupCandidate
     * @param status
     */
    private void stepDown(final long term, final boolean wakeupCandidate, final Status status) {
        logger.debug("Node {} stepDown, term={}, newTerm={}, wakeupCandidate={}.", getNodeId(), this.currTerm, term, wakeupCandidate);
        if (!this.state.isActive()) {
            return;
        }
        // 重置领导者信息
        resetLeaderId(PeerId.emptyPeer(), status);
        this.state = State.STATE_FOLLOWER;
        updateLastLeaderTimestamp(Utils.monotonicMs());
        if (term > this.currTerm) {
            this.currTerm = term;
            this.votedId = PeerId.emptyPeer();
            this.raftMetaStorage.setTermAndVoteFor(term, this.votedId);
        }
        // 如果当前节点不是学习者
        if (!isLearner()) {
            this.electionTimer.restart();
        } else {
            logger.info("Node {} is learner, election timer is not started.", this.nodeId);
        }
    }
    private void resetLeaderId(final PeerId newLeaderId, final Status status) {
        //判断当前传进来的节点是否为空
        if (newLeaderId.isEmpty()) {
            //如果为空，可能意味着正要进行选举操作，进行选举操作则意味着
            //集群失去了领导者，所以把当前节点记录的领导者信息设置为空即可
            this.leaderId = PeerId.emptyPeer();
        } else {
            //如果走到这里说明传进该方法中的节点并不为空，这可能是集群已经选举出了新的领导者
            //所以把当前节点记录的领导者信息设置为新的节点
            this.leaderId = newLeaderId.copy();
        }
    }
    private boolean isLearner() {
        return this.conf.listLearners().contains(this.serverId);
    }
    /**
     * 初始化日志存储器
     * @return
     */
    private boolean initLogStorage() {
        this.logStorage = this.serviceFactory.createLogStorage(this.options.getLogUri(), this.raftOptions);
        this.logManager = new LogManagerImpl();
        final LogManagerOptions opts = new LogManagerOptions();
        opts.setGroupId(this.groupId);
        opts.setLogEntryCodecFactory(this.serviceFactory.createLogEntryCodecFactory());
        opts.setLogStorage(this.logStorage);
        opts.setConfigurationManager(this.configManager);
        opts.setNodeMetrics(this.metrics);
        opts.setRaftOptions(this.raftOptions);
        return this.logManager.init(opts);
    }

    /**
     * 初始化元数据存储器，加载元数据文件后从中读取任期和投票节点
     * @return
     */
    private boolean initMetaStorage() {

        this.raftMetaStorage = this.serviceFactory.createRaftMetaStorage(this.options.getRaftMetaUri(), this.raftOptions);
        // 创建RaftMetaStorageOptions对象，这个对象是专门给元数据存储器使用的
        // 封装了元数据存储器需要的配置参数
        RaftMetaStorageOptions opts = new RaftMetaStorageOptions();
        // 把当前节点设置到opts中
        opts.setNode(this);
        // 在这里真正初始化了元数据存储器，初始化的过程中，会把元数据本地文件中的任期和为谁投票这些数据加载到内存中
        // 就赋值给元数据存储器对象中的两个对应成员变量，初始化失败则记录日志
        if (!this.raftMetaStorage.init(opts)) {
            logger.error("Node {} init meta storage failed, uri={}.", this.serverId, this.options.getRaftMetaUri());
            return false;
        }
        // 给当前节点的任期赋值
        this.currTerm = this.raftMetaStorage.getTerm();
        // 得到当前节点的投票记录，这里的copy在功能上就相当于一个深拷贝方法
        this.votedId = this.raftMetaStorage.getVoteFor().copy();
        return true;
    }

    /**
     * 修改 lastLeaderTimestamp
     * @param lastLeaderTimestamp
     */
    private void updateLastLeaderTimestamp(final long lastLeaderTimestamp) {
        this.lastLeaderTimestamp = lastLeaderTimestamp;
    }

    /**
     * 超时进行选举的方法
     * 1. 当前节点是否为跟随者
     * 2. 当前节点的Leader是否失效
     * 3. 重置Leader，开始预投票
     */
    private void handleElectionTimeout() {
        boolean doUnlock = true;
        this.writeLock.lock();
        try {
            if (this.state != State.STATE_FOLLOWER) {
                return;
            }
            if (isCurrentLeaderValid()) {
                return;
            }
            resetLeaderId(PeerId.emptyPeer(), new Status(RaftError.ERAFTTIMEDOUT, "Lost connection from leader %s", this.leaderId));
            doUnlock = false;
            // 进行预投票
            preVote();
        } finally {
            if (doUnlock) {
                writeLock.unlock();
            }
        }
    }

    /**
     * 预投票
     */
    private void preVote() {
        long oldTerm;
        try {
            logger.info("Node {} term {} start preVote.", getNodeId(), this.currTerm);
            if (!this.conf.contains(this.serverId)) {
                return;
            }
            oldTerm = this.currTerm;
        } finally {
            this.writeLock.unlock();
        }
        // 获取日志id
        LogId lastLogId = this.logManager.getLastLogId(true);
        boolean doUnlock = true;
        this.writeLock.lock();
        try {
            // 如果上面获取日志id时，此节点的任期被改变了，说明集群中出现leader了
            if (oldTerm != this.currTerm) {
                return;
            }
            // 初始化预投票器
            this.preVoteCtx.init(this.conf.getConf(), this.conf.isStable() ? null : this.conf.getOldConf());
            // 遍历集群中的节点给它们发信息
            for (final PeerId peer : this.conf.listPeers()) {
                if (peer.equals(this.serverId)) {
                    continue;
                }
                if (!this.rpcService.connect(peer.getEndpoint())) {
                    logger.warn("Node {} channel init failed, address={}.", getNodeId(), peer.getEndpoint());
                    continue;
                }
                // 设置回调对象，回调执行的是 handlePreVoteResponse()
                final OnPreVoteRpcDone done = new OnPreVoteRpcDone(peer, this.currTerm);
                // 构造请求内容
                done.request = RpcRequests.RequestVoteRequest.newBuilder()
                        .setPreVote(true)
                        .setGroupId(this.groupId)
                        .setServerId(this.serverId.toString())
                        .setPeerId(peer.toString())
                        .setTerm(this.currTerm + 1)
                        .setLastLogIndex(lastLogId.getIndex())
                        .setLastLogTerm(lastLogId.getTerm())
                        .build();
                // 发送预投票请求
                this.rpcService.preVote(peer.getEndpoint(), done.request, done);
            }
            // 发完之后给自己投一票
            this.preVoteCtx.grant(this.serverId);
            // 如果发现得到了集群中超过半数的节点，可以进行正式投票
            if (this.preVoteCtx.isGranted()) {
                doUnlock = false;
                electSelf();
            }
        } finally {
            if (doUnlock) {
                this.writeLock.unlock();
            }
        }
    }

    /**
     * 正式发起投票
     * 1. 如果自己是跟随者，停止超时选举定时器的工作
     * 2. 重置Leader，将自己的身份改为候选者，任期 + 1
     * 3. 遍历集群中的节点，开始拉票
     * 4. 给自己投一票
     */
    private void electSelf() {
        long oldTerm;
        try {
            logger.info("Node {} start vote and grant vote self, term={}.", getNodeId(), this.currTerm);
            if (!this.conf.contains(this.serverId)) {
                logger.warn("Node {} can't do electSelf as it is not in {}.", getNodeId(), this.conf);
                return;
            }
            // 如果当前节点是跟随者，停止超时选举器工作
            if (this.state == State.STATE_FOLLOWER) {
                logger.debug("Node {} stop election timer, term={}.", getNodeId(), this.currTerm);
                this.electionTimer.stop();
            }
            // 重置Leader
            resetLeaderId(PeerId.emptyPeer(), new Status(RaftError.ERAFTTIMEDOUT, "A follower's leader_id is reset to NULL as it begins to request_vote"));
            // 将节点的身份改为候选者，任期加一，初始化投票计数器并给自己投递选票
            this.state = State.STATE_CANDIDATE;
            this.currTerm++;
            this.votedId = this.serverId.copy();
            logger.debug("Node {} start vote timer, term={}.", getNodeId(), this.currTerm);
            this.voteCtx.init(this.conf.getConf(), this.conf.isStable() ? null : this.conf.getOldConf());
            oldTerm = this.currTerm;
        } finally {
            this.writeLock.unlock();
        }
        LogId lastLogId = this.logManager.getLastLogId(true);
        this.writeLock.lock();
        try {
            if (oldTerm != this.currTerm) {
                logger.warn("Node {} raise term {} when getLastLogId.",getNodeId(), this.currTerm);
                return;
            }
            // 给每一个节点发送正式投票请求
            for (PeerId peer : this.conf.listPeers()) {
                if (peer.equals(this.serverId)) {
                    continue;
                }
                if (!this.rpcService.connect(peer.getEndpoint())) {
                    continue;
                }
                // 构造回调对象，对方抉择是否投票之后返回响应，此节点使用回调处理响应
                final OnRequestVoteRpcDone done = new OnRequestVoteRpcDone(peer, this.currTerm, this);
                done.request = RpcRequests.RequestVoteRequest.newBuilder()
                        .setPreVote(false)
                        .setGroupId(this.groupId)
                        .setServerId(this.serverId.toString())
                        .setPeerId(peer.toString())
                        .setTerm(this.currTerm)
                        .setLastLogTerm(lastLogId.getTerm())
                        .setLastLogIndex(lastLogId.getIndex())
                        .build();
                this.rpcService.requestVote(peer.getEndpoint(), done.request, done);
            }
            // 投自己一票
            this.raftMetaStorage.setTermAndVoteFor(this.currTerm, this.serverId);
            this.voteCtx.grant(this.serverId);
            // 成功成为领导者
            if (this.voteCtx.isGranted()) {
                becomeLeader();
            }
        } finally {
            this.writeLock.unlock();
        }
    }

    /**
     * 此节点发起预投票后，收到对方的响应时要进行的回调
     * @param peerId
     * @param term
     * @param response
     */
    public void handlePreVoteResponse(final PeerId peerId, final long term, final RpcRequests.RequestVoteResponse response) {
        boolean doUnlock = true;
        this.writeLock.lock();
        try {
            if (this.state != State.STATE_FOLLOWER) {
                logger.warn("Node {} received invalid PreVoteResponse from {}, state not in STATE_FOLLOWER but {}.",
                        getNodeId(), peerId, this.state);
                return;
            }
            //判断当前节点的前后任期是否一致，不一致则直接退出该方法
            if (term != this.currTerm) {
                logger.warn("Node {} received invalid PreVoteResponse from {}, term={}, currTerm={}.", getNodeId(),
                        peerId, term, this.currTerm);
                return;
            }
            // 如果接收到响应后发现请求中的任期，也就是回复响应的节点的任期比自己大
            // 也直接退出该方法，并且让自己成为跟随者，处理预请求响应，正常情况下，没有发生分区故障的话，响应的节点应该当前发起预请求的任期一样
            if (response.getTerm() > this.currTerm) {
                logger.warn("Node {} received invalid PreVoteResponse from {}, term {}, expect={}.", getNodeId(), peerId,
                        response.getTerm(), this.currTerm);
                stepDown(response.getTerm(), false, new Status(RaftError.EHIGHERTERMRESPONSE,
                        "Raft node receives higher term pre_vote_response."));
                return;
            }
            logger.info("Node {} received PreVoteResponse from {}, term={}, granted={}.", getNodeId(), peerId,
                    response.getTerm(), response.getGranted());
            // 从响应中判断回复响应的节点是否给当前节点投票了
            if (response.getGranted()) {
                // 如果投票了，就在这里收集票数到计票器中
                this.preVoteCtx.grant(peerId);
                // 如果获得票数超过集群节点一半，就进入正式选举阶段
                if (this.preVoteCtx.isGranted()) {
                    doUnlock = false;
                    // 开始正式投票
                    electSelf();
                }
            }
        } finally {
            if (doUnlock) {
                this.writeLock.unlock();
            }
        }
    }
    /**
     * 作为服务端，处理来自其他节点的预投票请求
     * 1. 对方与自己是否在同一个集群中、当前节点是否有Leader、Leader是否有效
     * 2. 比较任期，如果没自己大，不支持；如果比自己大，支持但不会同步任期，因为这只是预投票
     * 3. 比较日志索引
     * @param request
     * @return
     */
    @Override
    public Message handlePreVoteRequest(RpcRequests.RequestVoteRequest request) {
        boolean doUnlock = true;
        this.writeLock.lock();
        try {
            // 如果本节点状态不佳，不参与选举
            if (!this.state.isActive()) {
                logger.warn("Node {} is not in active state, currTerm={}.", getNodeId(), this.currTerm);
                return RpcFactoryHelper.responseFactory()
                        .newResponse(RpcRequests.RequestVoteResponse.getDefaultInstance(), RaftError.EINVAL,
                                "Node %s is not in active state, state %s.", getNodeId(), this.state.name());
            }
            // candidateId代表候选者信息
            final PeerId candidateId = new PeerId();
            if (!candidateId.parse(request.getServerId())) {
                logger.warn("Node {} received PreVoteRequest from {} serverId bad format.", getNodeId(),
                        request.getServerId());
                //解析失败则发送失败响应
                return RpcFactoryHelper.responseFactory()
                        .newResponse(RpcRequests.RequestVoteResponse.getDefaultInstance(), RaftError.EINVAL,
                                "Parse candidateId failed: %s.", request.getServerId());
            }
            // 节点是否给候选者投票
            boolean granted = false;
            do {
                // 判断候选者是否和节点在同一个集群中
                if (!this.conf.contains(candidateId)) {
                    logger.warn("Node {} ignore PreVoteRequest from {} as it is not in conf <{}>.", getNodeId(),
                            request.getServerId(), this.conf);
                    //不在则退出循环，不必往下进行了
                    break;
                }
                // 如果节点是有leader的，并且Leader有效
                if (this.leaderId != null && !this.leaderId.isEmpty() && isCurrentLeaderValid()) {
                    logger.info("Node {} ignore PreVoteRequest from {}, term={}, currTerm={}, because the leader {}'s lease is still valid.",
                            getNodeId(), request.getServerId(), request.getTerm(), this.currTerm, this.leaderId);
                    break;
                }
                // 如果候选者的任期比节点小
                if (request.getTerm() < this.currTerm) {
                    logger.info("Node {} ignore PreVoteRequest from {}, term={}, currTerm={}.", getNodeId(),
                            request.getServerId(), request.getTerm(), this.currTerm);
                    break;
                }
                // 如果本节点是leader，检查一下候选者是否在复制器组中（有可能因为网络分区导致它没有在复制器组中）
                checkReplicator(candidateId);
                doUnlock = false;
                LogId lastLogId = this.logManager.getLastLogId(true);
                doUnlock = true;
                this.writeLock.lock();
                final LogId requestLastLogId = new LogId(request.getLastLogIndex(), request.getLastLogTerm());
                granted = requestLastLogId.compareTo(lastLogId) >= 0;
                logger.info("Node {} received PreVoteRequest from {}, term={}, currTerm={}, granted={}, requestLastLogId={},lastLogId={}.",
                        getNodeId(), request.getServerId(), request.getTerm(), this.currTerm, granted, requestLastLogId,lastLogId);
            } while (false);
            // 构建给候选者节点返回的响应
            return RpcRequests.RequestVoteResponse.newBuilder()
                    .setTerm(this.currTerm)
                    //这里参数为true，意味着当前节点给候选者投票了
                    .setGranted(granted)
                    .build();
        } finally {
            if (doUnlock) {
                this.writeLock.unlock();
            }
        }
    }
    private void checkReplicator(final PeerId candidateId) {
        if (this.state == State.STATE_LEADER) {
            this.replicatorGroup.checkReplicator(candidateId, false);
        }
    }

    /**
     * 作为服务端，处理来自其他节点的正式投票请求
     * 1. 比较任期，对方比自己小直接返回false，比自己大，就跟对方同步一下，跟自己一样也行
     * 2. 比较日志索引，对方必须大于等于自己的
     * 3. 确认自己在这个任期内没有投过票
     * @param request
     * @return
     */
    @Override
    public Message handleRequestVoteRequest(RpcRequests.RequestVoteRequest request) {
        boolean doUnlock = true;
        this.writeLock.lock();
        try {
            // 如果当前节点不健康，这个请求就返回false
            if (!this.state.isActive()) {
                logger.warn("Node {} is not in active state, currTerm={}.", getNodeId(), this.currTerm);
                return RpcFactoryHelper.responseFactory()
                        .newResponse(RpcRequests.RequestVoteResponse.getDefaultInstance(), RaftError.EINVAL,
                                "Node %s is not in active state, state %s.", getNodeId(), this.state.name());
            }
            // 使用 PeerId 接收候选者信息
            final PeerId candidateId = new PeerId();
            // 解析失败
            if (!candidateId.parse(request.getServerId())) {
                logger.warn("Node {} received RequestVoteRequest from {} serverId bad format.", getNodeId(),
                        request.getServerId());
                return RpcFactoryHelper.responseFactory()
                        .newResponse(RpcRequests.RequestVoteResponse.getDefaultInstance(), RaftError.EINVAL,
                                "Parse candidateId failed: %s.", request.getServerId());
            }
            do {
                // 如果候选者的任期大于当前节点的任期，才有可能投票给他
                if (request.getTerm() >= this.currTerm) {
                    logger.info("Node {} received RequestVoteRequest from {}, term={}, currTerm={}.", getNodeId(),
                            request.getServerId(), request.getTerm(), this.currTerm);
                    // 当此节点也是候选者并且其他候选者的任期较大时，此节点身份降级
                    if (request.getTerm() > this.currTerm) {
                        stepDown(request.getTerm(), false, new Status(RaftError.EHIGHERTERMRESPONSE, "Raft node receives higher term RequestVoteRequest."));
                    }
                } else {
                    // 如果那个节点任期没自己大，忽略
                    logger.info("Node {} ignore RequestVoteRequest from {}, term={}, currTerm={}.", getNodeId(), request.getServerId(), request.getTerm(), this.currTerm);
                    break;
                }
                doUnlock = false;
                this.writeLock.unlock();
                // 获取当前节点的最后一条日志id，开始比较日志
                LogId lastLogId = this.logManager.getLastLogId(true);
                doUnlock = true;
                this.writeLock.lock();
                // 当对方的任期没有自己大时，不会走到这里。
                // 如果对方任期比自己大，会将自己的任期与对方同步
                // 但是这里又不相等了，说明又有一个节点修改了此节点的任期，并且那个节点的任期更大，此时就不应该继续下面的流程了
                if (request.getTerm() != this.currTerm) {
                    logger.warn("Node {} raise term {} when get lastLogId.", getNodeId(), this.currTerm);
                    break;
                }
                // 比较一下日志索引
                final boolean logIsOk = new LogId(request.getLastLogIndex(), request.getLastLogTerm()).compareTo(lastLogId) >= 0;
                // 如果当前节点的日志的索引没有对方大，并且自己在这个任期中还没有投过票
                if (logIsOk && (this.votedId == null || this.votedId.isEmpty())) {
                    stepDown(request.getTerm(), false, new Status(RaftError.EVOTEFORCANDIDATE,
                            "Raft node votes for some candidate, step down to restart election_timer."));
                    // 记录并持久化投票信息
                    this.votedId = candidateId.copy();
                    this.raftMetaStorage.setVoteFor(candidateId);
                }
                // 再次确认任期与投票节点
                return RpcRequests.RequestVoteResponse.newBuilder()
                        .setTerm(this.currTerm)
                        .setGranted(request.getTerm() == this.currTerm && candidateId.equals(this.votedId))
                        .build();
            } while (false);
        } finally {
            //释放写锁的操作
            if (doUnlock) {
                this.writeLock.unlock();
            }
        }
        return null;
    }

    @Override
    public Message handleAppendEntriesRequest(RpcRequests.AppendEntriesRequest request, RpcRequestClosure done) {
        return null;
    }

    /**
     * 当前节点是候选者，给其他节点发送拉票请求后，处理响应
     * @param peerId 对方id
     * @param term 发送投票请求前，自己的任期
     * @param response 对方在斟酌后给我们的响应
     */
    public void handleRequestVoteResponse(final PeerId peerId, final long term, final RpcRequests.RequestVoteResponse response) {
        this.writeLock.lock();
        try {
            // 当前节点接收到响应时已经不是候选者了
            if (this.state != State.STATE_CANDIDATE) {
                return;
            }
            // 当前候选者的任期发生了变化，说明这个响应是之前的任期的，现在不作数了
            if (term != this.currTerm) {
                return;
            }
            // 对方的任期比自己大，那自己还抢什么Leader啊, 将身份降级为Follower
            if (response.getTerm() > this.currTerm) {
                stepDown(response.getTerm(), false, new Status(RaftError.EHIGHERTERMRESPONSE, "Raft node receives higher term request_vote_response"));
                return;
            }
            // 从响应中判断是否收到了投票
            if (response.getGranted()) {
                this.voteCtx.grant(peerId);
                if (this.voteCtx.isGranted()) {
                    becomeLeader();
                }
            }
        } finally {
            this.writeLock.unlock();
        }
    }

    /**
     * 正式投票后，接收到半数以上节点的支持，开始成为Leader
     * 调用这个方法前一般都已经加锁了
     */
    private void becomeLeader() {
        Requires.requireTrue(this.state == State.STATE_CANDIDATE, "Illegal state: " + this.state);
        logger.info("Node {} become leader of group, term={}, conf={}, oldConf={}.", getNodeId(), this.currTerm, this.conf, this.conf.getOldConf());
        this.state = State.STATE_LEADER;
        this.leaderId = this.serverId.copy();
        // 让集群中其他节点的任期与自己保持一致
        this.replicatorGroup.resetTerm(this.currTerm);
        // 将集群中的其他节点加入复制器组
        for (PeerId peer : this.conf.listPeers()) {
            if (peer.equals(this.serverId)) {
                continue;
            }
            logger.debug("Node {} add a replicator, term={}, peer={}.", getNodeId(), this.currTerm, peer);
            if(!this.replicatorGroup.addReplicator(peer)) {
                logger.error("Fail to add a replicator, peer={}.", peer);

            }
        }
        for (PeerId peer : this.conf.listLearners()) {
            if (!this.replicatorGroup.addReplicator(peer, ReplicatorType.Learner)) {
                logger.error("Fail to add a learner replicator, peer={}.", peer);
            }
        }
        if (this.confCtx.isBusy()) {
            throw new IllegalStateException();
        }
        // 刷新配置
        this.confCtx.flush(this.conf.getConf(), this.conf.getOldConf());
    }

    /**
     * 批量处理业务日志
     * @param tasks
     */
    private void executeApplyingTasks(List<LogEntryAndClosure> tasks) {
        this.writeLock.lock();
        try {
            final int size = tasks.size();
            // 当前节点如果不是领导者就不能处理日志
            if (this.state != State.STATE_LEADER) {
                final Status st = new Status();
                if (this.state != State.STATE_TRANSFERRING) {
                    st.setError(RaftError.EPERM, "Is not leader.");
                } else {
                    st.setError(RaftError.EBUSY, "Is transferring leadership.");
                }
                logger.debug("Node {} can't apply, status={}.", getNodeId(), st);
                // 节点不是leader不能操作日志，但是可以执行这批日志的回调方法来通知对应的业务层，告诉它，这批货我没法操作
                List<Closure> dones = tasks.stream().map(ele -> ele.done).filter(Objects::nonNull).collect(Collectors.toList());
                ThreadPoolsFactory.runInThread(this.groupId, () -> {
                    for (final Closure done : dones) {
                        done.run(st);
                    }
                });
                return;
            }
            // 如果是leader
            final List<LogEntry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                final LogEntryAndClosure task = tasks.get(i);
                // 业务层提交的所有业务日志，初始任期都为-1.
                // 如果不为-1，不操作这条日志，在回调中通知业务层失败消息
                if (task.expectedTerm != -1 && task.expectedTerm != currTerm) {
                    logger.debug("Node can't apply task whose expectedTerm={} doesn't match currTerm={}.", getNodeId(), task.expectedTerm, this.currTerm);
                    if (task.done != null) {
                        //执行回调方法，通知业务层操作失败了
                        final Status st = new Status(RaftError.EPERM, "expected_term=%d doesn't match current_term=%d",
                                task.expectedTerm, this.currTerm);
                        ThreadPoolsFactory.runClosureInThread(this.groupId, task.done, st);
                        task.reset();
                    }
                    continue;
                }
                task.entry.getId().setTerm(this.currTerm);
                task.entry.setType(EnumOutter.EntryType.ENTRY_TYPE_DATA);
                entries.add(task.entry);
                task.reset();
            }
            // 使用 LogManager 进行批量持久化
            this.logManager.appendEntries(entries, new LeaderStableClosure(entries));

        } finally {
            this.writeLock.unlock();
        }
    }
    /**
     * 是否出发选举超时，触发选举超时，当前节点可以成为候选者进行预投票
     * @return
     */
    private boolean isCurrentLeaderValid() {
        return Utils.monotonicMs() - this.lastLeaderTimestamp < this.options.getElectionTimeoutMs();
    }
    private int randomTimeout(final int timeoutMs) {
        return ThreadLocalRandom.current().nextInt(timeoutMs, timeoutMs + this.raftOptions.getMaxElectionDelayMs());
    }
    public void onError(final RaftException error) {
        logger.warn("Node {} got error: {}.", getNodeId(), error);
        this.writeLock.lock();
        try {
            if (this.state.compareTo(State.STATE_FOLLOWER) <= 0) {
                stepDown(this.currTerm, this.state == State.STATE_LEADER, new Status(RaftError.EBADNODE,
                        "Raft node(leader or candidate) is in error."));
            }
            if (this.state.compareTo(State.STATE_ERROR) < 0) {
                this.state = State.STATE_ERROR;
            }
        } finally {
            this.writeLock.unlock();
        }
    }
    @Override
    public PeerId getLeaderId() {
        this.readLock.lock();
        try {
            return this.leaderId.isEmpty() ? null : this.leaderId;
        } finally {
            this.readLock.unlock();
        }
    }
    @Override
    public String getGroupId() {
        return this.groupId;
    }

    @Override
    public RaftOptions getRaftOptions() {
        return null;
    }

    public PeerId getServerId() {
        return this.serverId;
    }

    @Override
    public NodeId getNodeId() {
        if (this.nodeId == null) {
            this.nodeId = new NodeId(this.groupId, this.serverId);
        }
        return this.nodeId;
    }

    public RaftClientService getRpcService() {
        return this.rpcService;
    }


}
