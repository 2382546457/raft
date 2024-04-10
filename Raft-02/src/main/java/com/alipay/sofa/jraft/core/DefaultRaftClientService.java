package com.alipay.sofa.jraft.core;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.ReplicatorGroup;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RemotingException;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.option.RpcOptions;
import com.alipay.sofa.jraft.rpc.InvokeContext;
import com.alipay.sofa.jraft.rpc.RpcClient;
import com.alipay.sofa.jraft.rpc.RpcRequests;
import com.alipay.sofa.jraft.rpc.closure.RpcResponseClosure;
import com.alipay.sofa.jraft.rpc.impl.AbstractClientService;
import com.alipay.sofa.jraft.rpc.RaftClientService;
import com.alipay.sofa.jraft.rpc.impl.FutureImpl;
import com.alipay.sofa.jraft.util.Endpoint;
import com.alipay.sofa.jraft.util.Utils;
import com.alipay.sofa.jraft.util.concurrent.DefaultFixedThreadsExecutorGroupFactory;
import com.alipay.sofa.jraft.util.concurrent.FixedThreadsExecutorGroup;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-05 00:04
 */
public class DefaultRaftClientService extends AbstractClientService implements RaftClientService {
    private static final Logger logger = LoggerFactory.getLogger(DefaultRaftClientService.class);

    private final FixedThreadsExecutorGroup appendEntriesExecutors;

    private final ConcurrentMap<Endpoint, Executor> appendEntriesExecutorMap = new ConcurrentHashMap<>();

    private NodeOptions nodeOptions;
    private final ReplicatorGroup rgGroup;

    @Override
    protected void configRpcClient(final RpcClient rpcClient) {
        rpcClient.registerConnectionEventListener(this.rgGroup);
    }

    public DefaultRaftClientService(final ReplicatorGroup rgGroup) {
        this(rgGroup, DefaultFixedThreadsExecutorGroupFactory.INSTANCE.newExecutorGroup(
                Utils.APPEND_ENTRIES_THREADS_SEND, "Append-Entries-Thread-Send", Utils.MAX_APPEND_ENTRIES_TASKS_PER_THREAD,
                true));
    }

    /**
     * 这个构造方法会在NodeImpl类的init方法中被调用，调用的时候会传进来执行器组，会为该类的执行器组成员变量赋值
     */
    public DefaultRaftClientService(final ReplicatorGroup rgGroup,
                                    final FixedThreadsExecutorGroup customAppendEntriesExecutors) {
        this.rgGroup = rgGroup;
        this.appendEntriesExecutors = customAppendEntriesExecutors;
    }

    /**
     * 初始化的方法，初始化的核心逻辑在该类的父类init方法中进行
     * @param rpcOptions
     * @return
     */
    @Override
    public synchronized boolean init(final RpcOptions rpcOptions) {
        final boolean ret = super.init(rpcOptions);
        if (ret) {
            this.nodeOptions = (NodeOptions) rpcOptions;
        }
        return ret;
    }

    /**
     * 客户端发送预投票请求的方法
     */
    @Override
    public Future<Message> preVote(final Endpoint endpoint, final RpcRequests.RequestVoteRequest request,
                                   final RpcResponseClosure<RpcRequests.RequestVoteResponse> done) {
        if (!checkConnection(endpoint, true)) {
            return onConnectionFail(endpoint, request, done, this.rpcExecutor);
        }

        return invokeWithDone(endpoint, request, done, this.nodeOptions.getElectionTimeoutMs());
    }

    /**
     * 发送正式投票请求的方法
     */
    @Override
    public Future<Message> requestVote(final Endpoint endpoint, final RpcRequests.RequestVoteRequest request,
                                       final RpcResponseClosure<RpcRequests.RequestVoteResponse> done) {
        if (!checkConnection(endpoint, true)) {
            return onConnectionFail(endpoint, request, done, this.rpcExecutor);
        }

        return invokeWithDone(endpoint, request, done, this.nodeOptions.getElectionTimeoutMs());
    }

    /**
     * 发送心跳和日志给跟随者的方法
     * 这个方法的回调逻辑挺复杂的，所以使用线程对应节点的方法 (而不是线程池方式) 加快处理速度
     */
    @Override
    public Future<Message> appendEntries(final Endpoint endpoint, final RpcRequests.AppendEntriesRequest request,
                                         final int timeoutMs, final RpcResponseClosure<RpcRequests.AppendEntriesResponse> done) {
        final Executor executor = this.appendEntriesExecutorMap.computeIfAbsent(endpoint, k -> appendEntriesExecutors.next());

        if (!checkConnection(endpoint, true)) {
            return onConnectionFail(endpoint, request, done, executor);
        }
        return invokeWithDone(endpoint, request, done, timeoutMs, executor);
    }


    @Override
    public Future<Message> getFile(final Endpoint endpoint, final RpcRequests.GetFileRequest request, final int timeoutMs,
                                   final RpcResponseClosure<RpcRequests.GetFileResponse> done) {
        final InvokeContext ctx = new InvokeContext();
        ctx.put(InvokeContext.CRC_SWITCH, true);
        return invokeWithDone(endpoint, request, ctx, done, timeoutMs);
    }

    @Override
    public Future<Message> installSnapshot(final Endpoint endpoint, final RpcRequests.InstallSnapshotRequest request,
                                           final RpcResponseClosure<RpcRequests.InstallSnapshotResponse> done) {
        return invokeWithDone(endpoint, request, done, this.rpcOptions.getRpcInstallSnapshotTimeout());
    }

    @Override
    public Future<Message> timeoutNow(final Endpoint endpoint, final RpcRequests.TimeoutNowRequest request, final int timeoutMs,
                                      final RpcResponseClosure<RpcRequests.TimeoutNowResponse> done) {
        return invokeWithDone(endpoint, request, done, timeoutMs);
    }

    @Override
    public Future<Message> readIndex(final Endpoint endpoint, final RpcRequests.ReadIndexRequest request, final int timeoutMs,
                                     final RpcResponseClosure<RpcRequests.ReadIndexResponse> done) {
        return invokeWithDone(endpoint, request, done, timeoutMs);
    }

    private Future<Message> onConnectionFail(final Endpoint endpoint, final Message request, Closure done, final Executor executor) {
        final FutureImpl<Message> future = new FutureImpl<>();
        executor.execute(() -> {
            final String fmt = "Check connection[%s] fail and try to create new one";
            if (done != null) {
                try {
                    done.run(new Status(RaftError.EINTERNAL, fmt, endpoint));
                } catch (final Throwable t) {
                    logger.error("Fail to run RpcResponseClosure, the request is {}.", request, t);
                }
            }
            if (!future.isDone()) {
                future.failure(new RemotingException(String.format(fmt, endpoint)));
            }
        });
        return future;
    }
}