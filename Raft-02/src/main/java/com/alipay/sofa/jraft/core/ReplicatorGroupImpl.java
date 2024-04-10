package com.alipay.sofa.jraft.core;

import com.alipay.sofa.jraft.ReplicatorGroup;
import com.alipay.sofa.jraft.entity.NodeId;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.option.ReplicatorGroupOptions;
import com.alipay.sofa.jraft.option.ReplicatorOptions;
import com.alipay.sofa.jraft.rpc.RaftClientService;
import com.alipay.sofa.jraft.util.Requires;
import com.alipay.sofa.jraft.util.ThreadId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author : 小何
 * @Description : 复制器组，管理所有的复制器对象
 * 当一个节点当选为 Leader 时，会将集群中的其他节点封装为复制器，放入复制器组中
 * @date : 2024-04-05 18:27
 */
public class ReplicatorGroupImpl implements ReplicatorGroup {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorGroupImpl.class);
    /**
     * key : 节点
     * threadId : replicator
     */
    private final ConcurrentMap<PeerId, ThreadId> replicatorMap = new ConcurrentHashMap<>();
    private ReplicatorOptions commonOptions;
    /**
     * 心跳超时时间
     */
    private int dynamicTimeoutMs = -1;
    /**
     * 选举超时时间
     */
    private int electionTimeoutMs = -1;
    private RaftOptions raftOptions;
    /**
     * 出现异常的节点的映射关系
     * key : 出现异常的节点
     * value : 该节点的身份是普通跟随者还是学习者
     */
    private final Map<PeerId, ReplicatorType> failureReplicators = new ConcurrentHashMap<>();


    /**
     * 初始化复制器组
     * @param nodeId
     * @param opts
     * @return
     */
    @Override
    public boolean init(NodeId nodeId, ReplicatorGroupOptions opts) {
        this.dynamicTimeoutMs = opts.getHeartbeatTimeoutMs();
        this.electionTimeoutMs = opts.getElectionTimeoutMs();
        this.raftOptions = opts.getRaftOptions();
        this.commonOptions = new ReplicatorOptions();
        this.commonOptions.setDynamicHeartBeatTimeoutMs(this.dynamicTimeoutMs);
        this.commonOptions.setElectionTimeoutMs(this.electionTimeoutMs);
        this.commonOptions.setRaftRpcService(opts.getRaftRpcClientService());
        this.commonOptions.setNode(opts.getNode());
        this.commonOptions.setTerm(0);
        this.commonOptions.setGroupId(nodeId.getGroupId());
        this.commonOptions.setLogManager(opts.getLogManager());
        this.commonOptions.setServerId(nodeId.getPeerId());
        this.commonOptions.setTimerManager(opts.getTimerManager());
        return true;
    }

    @Override
    public boolean addReplicator(PeerId peer, ReplicatorType replicatorType, boolean sync) {
        Requires.requireTrue(this.commonOptions.getTerm() != 0);
        this.failureReplicators.remove(peer);
        if (this.replicatorMap.containsKey(peer)) {
            return true;
        }
        // 默认使用复制器组通用的配置参数
        final ReplicatorOptions opts = this.commonOptions == null ? new ReplicatorOptions() : this.commonOptions.copy();
        opts.setReplicatorType(replicatorType);
        opts.setPeerId(peer);
        // 默认不会走这里
        if (!sync) {
            // 尝试建立连接，如果失败则返回false
            final RaftClientService client = opts.getRaftRpcService();
            if (client != null && !client.checkConnection(peer.getEndpoint(), true)) {
                logger.error("Fail to check replicator connection to peer={}, replicatorType={}", peer, replicatorType);
                this.failureReplicators.put(peer, replicatorType);
                return false;
            }
        }
        ThreadId id = Replicator.start(opts, this.raftOptions);
        if (id == null) {
            logger.error("Fail to start replicator to peer={}, replicatorType={}.", peer, replicatorType);
            this.failureReplicators.put(peer, replicatorType);
            return false;
        }
        return this.replicatorMap.put(peer, id) == null;
    }

    @Override
    public ThreadId getReplicator(PeerId peer) {
        return this.replicatorMap.get(peer);
    }

    @Override
    public void checkReplicator(PeerId peer, boolean lockNode) {

    }

    @Override
    public boolean resetTerm(final long newTerm) {
        if (newTerm <= this.commonOptions.getTerm()) {
            return false;
        }
        this.commonOptions.setTerm(newTerm);
        return true;
    }

    @Override
    public boolean contains(final PeerId peer) {
        return this.replicatorMap.containsKey(peer);
    }

    @Override
    public void describe(final Printer out) {
        out.print("  replicators: ") //
                .println(this.replicatorMap.values());
        out.print("  failureReplicators: ") //
                .println(this.failureReplicators);
    }
}
