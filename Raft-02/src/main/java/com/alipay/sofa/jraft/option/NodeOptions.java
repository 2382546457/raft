package com.alipay.sofa.jraft.option;

import com.alipay.remoting.util.StringUtils;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.core.ElectionPriority;
import com.alipay.sofa.jraft.util.Copiable;
import com.alipay.sofa.jraft.util.JRaftServiceFactory;
import com.alipay.sofa.jraft.util.JRaftServiceLoader;
import com.alipay.sofa.jraft.util.Utils;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-05 00:07
 */
public class NodeOptions extends RpcOptions implements Copiable<NodeOptions> {
    /**
     * DefaultRaftServiceFactory
     */
    private static final JRaftServiceFactory defaultServiceFactory = JRaftServiceLoader.load(JRaftServiceFactory.class).first();

    /**
     * 超时选举时间，默认1s (leader维持的有效时间)
     */
    private int electionTimeoutMs = 1000;
    /**
     * 默认不使用优先级
     */
    private int electionPriority = ElectionPriority.Disable;

    /**
     * 衰减优先级的辅助变量
     */
    private int decayPriorityGap = 10;
    /**
     * Leader祖约时间，租约时间内Follower不得发起选举
     */
    private int leaderLeaseTimeRatio = 90;
    /**
     * 当前节点所属的配置信息
     */
    private Configuration initialConf = new Configuration();
    /**
     * 日志存放路径
     */
    private String logUri;
    /**
     * 元数据文件的存储路径
     */
    private String raftMetaUri;

    /**
     * 不禁用集群客户端，通过集群客户端可以和集群通信
     */
    private boolean disableCli = false;

    /**
     * 是否要共享定时任务管理器
     */
    private boolean sharedTimerPool = false;
    /**
     * 全局定时任务管理器中的线程池的核心线程数量，CPU核数的三倍，最大20
     */
    private int timerPoolSize = Utils.cpus() * 3 > 20 ? 20 : Utils.cpus() * 3;
    /**
     * 集群客户端的线程池大小
     */
    private int cliRpcThreadPoolSize = Utils.cpus();
    /**
     * JRaft内部节点的服务端线程池大小，默认为 CPU * 6
     */
    private int raftRpcThreadPoolSize = Utils.cpus() * 6;
    /**
     * 是否开启性能监控，默认不开启
     */
    private boolean enableMetrics = false;
    /**
     * 是否共享 超时选举定时器
     */
    private boolean sharedElectionTimer = false;
    /**
     * 是否共享投票定时器
     */
    private boolean sharedVoteTimer = false;

    /**
     * 是否共享检测降级定时器
     */
    private boolean sharedStepDownTimer = false;
    /**
     * 是否共享快照生成器
     */
    private boolean sharedSnapshotTimer = false;

    /**
     * DefaultRaftServiceFactory，可以创建日志存储器、源数据存储器、编码解码器
     */
    private JRaftServiceFactory serviceFactory = defaultServiceFactory;
    private RaftOptions raftOptions = new RaftOptions();


    // ------------------------------------------------ get and set -------------------------------------------------------------
    public void setEnableMetrics(final boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
    }


    public int getCliRpcThreadPoolSize() {
        return this.cliRpcThreadPoolSize;
    }

    public void setCliRpcThreadPoolSize(final int cliRpcThreadPoolSize) {
        this.cliRpcThreadPoolSize = cliRpcThreadPoolSize;
    }

    public boolean isEnableMetrics() {
        return this.enableMetrics;
    }

    public int getRaftRpcThreadPoolSize() {
        return this.raftRpcThreadPoolSize;
    }

    public void setRaftRpcThreadPoolSize(final int raftRpcThreadPoolSize) {
        this.raftRpcThreadPoolSize = raftRpcThreadPoolSize;
    }

    public boolean isSharedTimerPool() {
        return this.sharedTimerPool;
    }

    public void setSharedTimerPool(final boolean sharedTimerPool) {
        this.sharedTimerPool = sharedTimerPool;
    }

    public int getTimerPoolSize() {
        return this.timerPoolSize;
    }

    public void setTimerPoolSize(final int timerPoolSize) {
        this.timerPoolSize = timerPoolSize;
    }

    public RaftOptions getRaftOptions() {
        return this.raftOptions;
    }

    public void setRaftOptions(final RaftOptions raftOptions) {
        this.raftOptions = raftOptions;
    }

    public void validate() {
        if (StringUtils.isBlank(this.logUri)) {
            throw new IllegalArgumentException("Blank logUri");
        }
        if (StringUtils.isBlank(this.raftMetaUri)) {
            throw new IllegalArgumentException("Blank raftMetaUri");
        }
    }

    public JRaftServiceFactory getServiceFactory() {
        return this.serviceFactory;
    }

    public void setServiceFactory(final JRaftServiceFactory serviceFactory) {
        this.serviceFactory = serviceFactory;
    }

    public int getElectionPriority() {
        return this.electionPriority;
    }

    public void setElectionPriority(final int electionPriority) {
        this.electionPriority = electionPriority;
    }

    public int getDecayPriorityGap() {
        return this.decayPriorityGap;
    }

    public void setDecayPriorityGap(final int decayPriorityGap) {
        this.decayPriorityGap = decayPriorityGap;
    }

    public int getElectionTimeoutMs() {
        return this.electionTimeoutMs;
    }

    public void setElectionTimeoutMs(final int electionTimeoutMs) {
        this.electionTimeoutMs = electionTimeoutMs;
    }

    public int getLeaderLeaseTimeRatio() {
        return this.leaderLeaseTimeRatio;
    }

    public void setLeaderLeaseTimeRatio(final int leaderLeaseTimeRatio) {
        if (leaderLeaseTimeRatio <= 0 || leaderLeaseTimeRatio > 100) {
            throw new IllegalArgumentException("leaderLeaseTimeRatio: " + leaderLeaseTimeRatio
                    + " (expected: 0 < leaderLeaseTimeRatio <= 100)");
        }
        this.leaderLeaseTimeRatio = leaderLeaseTimeRatio;
    }

    public int getLeaderLeaseTimeoutMs() {
        return this.electionTimeoutMs * this.leaderLeaseTimeRatio / 100;
    }


    public Configuration getInitialConf() {
        return this.initialConf;
    }

    public void setInitialConf(final Configuration initialConf) {
        this.initialConf = initialConf;
    }


    public String getLogUri() {
        return this.logUri;
    }

    public void setLogUri(final String logUri) {
        this.logUri = logUri;
    }

    public String getRaftMetaUri() {
        return this.raftMetaUri;
    }

    public void setRaftMetaUri(final String raftMetaUri) {
        this.raftMetaUri = raftMetaUri;
    }


    public boolean isDisableCli() {
        return this.disableCli;
    }

    public void setDisableCli(final boolean disableCli) {
        this.disableCli = disableCli;
    }

    public boolean isSharedElectionTimer() {
        return this.sharedElectionTimer;
    }

    public void setSharedElectionTimer(final boolean sharedElectionTimer) {
        this.sharedElectionTimer = sharedElectionTimer;
    }

    public boolean isSharedVoteTimer() {
        return this.sharedVoteTimer;
    }

    public void setSharedVoteTimer(final boolean sharedVoteTimer) {
        this.sharedVoteTimer = sharedVoteTimer;
    }

    public boolean isSharedStepDownTimer() {
        return this.sharedStepDownTimer;
    }

    public void setSharedStepDownTimer(final boolean sharedStepDownTimer) {
        this.sharedStepDownTimer = sharedStepDownTimer;
    }

    public boolean isSharedSnapshotTimer() {
        return this.sharedSnapshotTimer;
    }

    public void setSharedSnapshotTimer(final boolean sharedSnapshotTimer) {
        this.sharedSnapshotTimer = sharedSnapshotTimer;
    }


    //又是一个深拷贝方法
    @Override
    public NodeOptions copy() {
        final NodeOptions nodeOptions = new NodeOptions();
        nodeOptions.setElectionTimeoutMs(this.electionTimeoutMs);
        nodeOptions.setElectionPriority(this.electionPriority);
        nodeOptions.setDecayPriorityGap(this.decayPriorityGap);
        nodeOptions.setDisableCli(this.disableCli);
        nodeOptions.setSharedTimerPool(this.sharedTimerPool);
        nodeOptions.setTimerPoolSize(this.timerPoolSize);
        nodeOptions.setCliRpcThreadPoolSize(this.cliRpcThreadPoolSize);
        nodeOptions.setRaftRpcThreadPoolSize(this.raftRpcThreadPoolSize);
        nodeOptions.setEnableMetrics(this.enableMetrics);
        nodeOptions.setRaftOptions(this.raftOptions == null ? new RaftOptions() : this.raftOptions.copy());
        nodeOptions.setSharedElectionTimer(this.sharedElectionTimer);
        nodeOptions.setSharedVoteTimer(this.sharedVoteTimer);
        nodeOptions.setSharedStepDownTimer(this.sharedStepDownTimer);
        nodeOptions.setSharedSnapshotTimer(this.sharedSnapshotTimer);
        nodeOptions.setRpcConnectTimeoutMs(super.getRpcConnectTimeoutMs());
        nodeOptions.setRpcDefaultTimeout(super.getRpcDefaultTimeout());
        nodeOptions.setRpcInstallSnapshotTimeout(super.getRpcInstallSnapshotTimeout());
        nodeOptions.setRpcProcessorThreadPoolSize(super.getRpcProcessorThreadPoolSize());
        nodeOptions.setEnableRpcChecksum(super.isEnableRpcChecksum());
        nodeOptions.setMetricRegistry(super.getMetricRegistry());

        return nodeOptions;
    }

    @Override
    public String toString() {
        return "NodeOptions{" + "electionTimeoutMs=" + this.electionTimeoutMs + ", electionPriority="
                + this.electionPriority + ", decayPriorityGap=" + this.decayPriorityGap + ", leaderLeaseTimeRatio="
                + this.leaderLeaseTimeRatio + ",  initialConf=" + this.initialConf + ", logUri='" + this.logUri + '\''
                + ", raftMetaUri='" + this.raftMetaUri + '\'' + ",disableCli=" + this.disableCli
                + ", sharedTimerPool=" + this.sharedTimerPool + ", timerPoolSize=" + this.timerPoolSize
                + ", cliRpcThreadPoolSize=" + this.cliRpcThreadPoolSize + ", raftRpcThreadPoolSize="
                + this.raftRpcThreadPoolSize + ", enableMetrics=" + this.enableMetrics + ", " +
                ", sharedElectionTimer=" + this.sharedElectionTimer + ", sharedVoteTimer="
                + this.sharedVoteTimer + ", sharedStepDownTimer=" + this.sharedStepDownTimer + ", sharedSnapshotTimer="
                + this.sharedSnapshotTimer + ", serviceFactory=" + this.serviceFactory + ", " +
                " raftOptions=" + this.raftOptions + "} " + super.toString();
    }
}
