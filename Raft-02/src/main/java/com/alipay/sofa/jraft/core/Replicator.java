package com.alipay.sofa.jraft.core;

import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.option.ReplicatorOptions;
import com.alipay.sofa.jraft.rpc.RaftClientService;
import com.alipay.sofa.jraft.rpc.RpcRequests;
import com.alipay.sofa.jraft.rpc.RpcUtils;
import com.alipay.sofa.jraft.rpc.closure.RpcResponseClosure;
import com.alipay.sofa.jraft.rpc.closure.RpcResponseClosureAdapter;
import com.alipay.sofa.jraft.util.ThreadId;
import com.alipay.sofa.jraft.util.Utils;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author : 小何
 * @Description : 复制器，Leader中维护一个复制器组，复制器组中是整个集群中的所有跟随者，每一个跟随者都会被封装为复制器
 * @date : 2024-04-05 15:17
 */
@ThreadSafe
public class Replicator implements ThreadId.OnError {
    private static final Logger logger = LoggerFactory.getLogger(Replicator.class);
    /**
     * 要发送给这个跟随者的日志下标
     */
    private volatile long nextIndex;
    private final RaftClientService rpcService;
    /**
     * 这个复制器所属的 ThreadID
     */
    protected ThreadId id;
    /**
     * 这个复制器对象的配置参数
     */
    private final ReplicatorOptions options;

    private final RaftOptions raftOptions;
    /**
     * 全局定时任务管理器
     */
    private final Scheduler timerManager;
    /**
     * 上一次发送心跳的时间
     */
    private volatile long lastRpcSendTimestamp;
    /**
     * 发送的心跳次数
     */
    private volatile long heartbeatCounter = 0;
    /**
     * 心跳定时器
     */
    private ScheduledFuture<?> heartbeatTimer;
    /**
     * 正在执行的心跳任务
     */
    private Future<Message> heartbeatInFly;

    public Replicator(ReplicatorOptions replicatorOptions, RaftOptions raftOptions) {
        super();
        this.options = replicatorOptions;
        // 要发送的日志默认为 Leader 最后一条日志索引加一
        this.nextIndex = this.options.getLogManager().getLastLogIndex() + 1;
        this.timerManager = replicatorOptions.getTimerManager();
        this.raftOptions = raftOptions;
        this.rpcService = replicatorOptions.getRaftRpcService();
    }

    public static ThreadId start(final ReplicatorOptions opts, final RaftOptions raftOptions) {
        // 创建复制器
        final Replicator r = new Replicator(opts, raftOptions);
        // 检查rpc能否与复制器代表的从节点建立连接
        if (!r.rpcService.connect(opts.getPeerId().getEndpoint())) {
            logger.error("Fail to init sending channel to {}.", opts.getPeerId());
            return null;
        }
        r.id = new ThreadId(r, r);
        r.id.lock();
        r.lastRpcSendTimestamp = Utils.monotonicMs();
        // 启动心跳定时器，这个定时器会定时向节点发送心跳消息
        r.startHeartbeatTimer(Utils.nowMs());
        // 发送探针请求，Leader当选之后要向其他节点发送，告诉对应节点当前集群已经有Leader了
        // 节点接收到之后会回复响应，同时将自己的日志索引下标告诉Leader，Leader收到之后决定从哪个下标开始给节点传输数据
        // 如果差的太多就使用快照
        r.sendProbeRequest();
        return r.id;
    }

    /**
     * 启动心跳定时器。Leader利用心跳定时器给Follower发送心跳，维持身份
     * @param startMs
     */
    private void startHeartbeatTimer(final long startMs) {
        final long dueTime = startMs + this.options.getDynamicHeartBeatTimeoutMs();
        try {
            // 使用全局定时任务管理器提交一个定时任务，在这个定时任务中执行发送心跳的方法
            this.heartbeatTimer = this.timerManager.schedule(() -> onTimeout(this.id), dueTime - Utils.nowMs(), TimeUnit.MILLISECONDS);
        } catch (final Exception e) {
            logger.error("Fail to schedule heartbeat timer.", e);
            onTimeout(this.id);
        }
    }
    private static void onTimeout(final ThreadId id) {
        if (id != null) {
            id.setError(RaftError.ETIMEDOUT.getNumber());
        } else {
            logger.warn("Replicator id is null when timeout, maybe it's destroyed.");
        }
    }
    @Override
    public void onError(ThreadId id, Object data, int errorCode) {
        final Replicator r = (Replicator) data;
        RpcUtils.runInThread(() -> sendHeartbeat(id));
    }

    /**
     * 发送心跳请求，也就是发送空日志信息
     * @param id
     */
    private static void sendHeartbeat(final ThreadId id) {
        final Replicator r = (Replicator) id.lock();
        if (r == null) {
            return;
        }
        r.sendEmptyEntries(true);
    }

    /**
     * 发送探针消息
     */
    private void sendProbeRequest() {
        sendEmptyEntries(false);
    }
    private void sendEmptyEntries(final boolean isHeartbeat) {
        sendEmptyEntries(isHeartbeat, null);
    }

    /**
     * 发送心跳或者探针消息的方法
     * @param isHeartbeat
     * @param heartBeatClosure
     */
    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    private void sendEmptyEntries(final boolean isHeartbeat, final RpcResponseClosure<RpcRequests.AppendEntriesResponse> heartBeatClosure) {
        final RpcRequests.AppendEntriesRequest.Builder rb = RpcRequests.AppendEntriesRequest.newBuilder();
        // 前一个日志索引先写死
        fillCommonFields(rb, 0, isHeartbeat);
        try {
            // 当前时间
            final long monotonicSendTimeMs = Utils.monotonicMs();
            // 判断心跳还是探针
            if (isHeartbeat) {
                logger.info("Leader发送了心跳消息.");
                final RpcRequests.AppendEntriesRequest request = rb.build();
                this.heartbeatCounter++;
                RpcResponseClosure<RpcRequests.AppendEntriesResponse> heartbeatDone;
                if (heartBeatClosure != null) {
                    heartbeatDone = heartBeatClosure;
                } else {
                    heartbeatDone = new RpcResponseClosureAdapter<RpcRequests.AppendEntriesResponse>() {
                        @Override
                        public void run(final Status status) {
                            // 在创建的新的回调方法中，收到心跳响应之后，会回调这个方法，在这个方法中会提交下一个心跳消息任务给全局定时器
                            onHeartbeatReturned(Replicator.this.id, status, request, getResponse(), monotonicSendTimeMs);
                        }
                    };
                }
                this.heartbeatInFly = this.rpcService.appendEntries(this.options.getPeerId().getEndpoint(), request, this.options.getElectionTimeoutMs() / 2, heartbeatDone);
            } else {
                // 探针消息
                rb.setData(ByteString.EMPTY);
                final RpcRequests.AppendEntriesRequest request = rb.build();
                final Future<Message> rpcFuture = this.rpcService.appendEntries(
                        this.options.getPeerId().getEndpoint(),
                        request,
                        -1,
                        new RpcResponseClosureAdapter<RpcRequests.AppendEntriesResponse>() {
                            @Override
                            public void run(Status status) {
                                // 探针消息的回调暂时不做处理
                            }
                        }
                );
            }
            logger.debug("Node {} send HeartbeatRequest to {} term {} lastCommittedIndex {}", this.options.getNode()
                    .getNodeId(), this.options.getPeerId(), this.options.getTerm(), rb.getCommittedIndex());
        } finally {
            // 释放锁，发送心跳之前加锁，发送成功之后释放锁
            unlockId();
        }
    }

    /**
     * 填充 AppendEntriesRequest请求中的公共字段
     * @param rb
     * @param preLogIndex
     * @param isHeartbeat
     * @return
     */
    private boolean fillCommonFields(final RpcRequests.AppendEntriesRequest.Builder rb, long preLogIndex, final boolean isHeartbeat) {
        rb.setTerm(this.options.getTerm());
        rb.setGroupId(this.options.getGroupId());
        rb.setServerId(this.options.getServerId().toString());
        rb.setPrevLogIndex(preLogIndex);
        rb.setPrevLogTerm(0);
        rb.setCommittedIndex(0);
        return true;
    }

    /**
     * 接收到心跳请求的响应之后回调的方法
     * @param id
     * @param status
     * @param request
     * @param response
     * @param rpcSendTime
     */
    static void onHeartbeatReturned(final ThreadId id,
                                    final Status status,
                                    final RpcRequests.AppendEntriesRequest request,
                                    final RpcRequests.AppendEntriesResponse response,
                                    final long rpcSendTime) {
        // 回调时 ThreadId为空，说明复制器已经销毁
        if (id == null) {
            return;
        }
        final long startTimeMs = Utils.nowMs();
        Replicator r;
        if ((r = (Replicator) id.lock()) == null) {
            return;
        }
        boolean doUnlock = true;
        try {
            final boolean isLogDebugEnabled = logger.isDebugEnabled();
            StringBuilder sb = null;
            if (isLogDebugEnabled) {
                sb = new StringBuilder("Node ")
                        .append(r.options.getGroupId())
                        .append(':')
                        .append(r.options.getServerId())
                        .append(" received HeartbeatResponse from ")
                        .append(r.options.getPeerId())
                        .append(" prevLogIndex=")
                        .append(request.getPrevLogIndex())
                        .append(" prevLogTerm=")
                        .append(request.getPrevLogTerm());
            }
            // 如果对方的任期比自己大
            if (response.getTerm() > r.options.getTerm()) {
                if (isLogDebugEnabled) {
                    sb.append(" fail, response term ")
                            .append(response.getTerm())
                            .append(" lastLogIndex ")
                            .append(response.getLastLogIndex());
                    logger.debug(sb.toString());
                }
                final NodeImpl node = r.options.getNode();
                // TODO 发送心跳消息，结果发现对方的 term 比自己大
                return;
            }
            // 如果响应状态为失败
            if (!response.getSuccess() && response.hasLastLogIndex()) {
                if (isLogDebugEnabled) {
                    sb.append(" fail, response term ")
                            .append(response.getTerm())
                            .append(" lastLogIndex ")
                            .append(response.getLastLogIndex());
                    logger.debug(sb.toString());
                }
                logger.warn("Heartbeat to peer {} failure, try to send a probe request.", r.options.getPeerId());
                doUnlock = false;
                // 发送探针消息
                r.sendProbeRequest();
                // 启动心跳定时器，开启下一次心跳定时任务
                r.startHeartbeatTimer(startTimeMs);
                return;
            }
            if (isLogDebugEnabled) {
                logger.debug(sb.toString());
            }
            if (rpcSendTime > r.lastRpcSendTimestamp) {
                r.lastRpcSendTimestamp = rpcSendTime;
            }
            // 开启下一次心跳定时任务
            r.startHeartbeatTimer(startTimeMs);
        } finally {
            if (doUnlock) {
                //在这里把锁释放了
                id.unlock();
            }
        }
    }

    private void unlockId() {
        if (this.id == null) {
            return;
        }
        this.id.unlock();
    }

}
