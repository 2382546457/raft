package com.alipay.sofa.jraft.rpc.impl;

import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.InvokeTimeoutException;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RemotingException;
import com.alipay.sofa.jraft.option.RpcOptions;
import com.alipay.sofa.jraft.rpc.*;
import com.alipay.sofa.jraft.util.Endpoint;
import com.alipay.sofa.jraft.util.NamedThreadFactory;
import com.alipay.sofa.jraft.util.ThreadPoolMetricSet;
import com.alipay.sofa.jraft.util.ThreadPoolUtil;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-01 10:18
 */
public abstract class AbstractClientService implements ClientService {
    protected static final Logger logger = LoggerFactory.getLogger(AbstractClientService.class);

    static {
        // 加载 Protobuf描述文件，并且注册 ProtobufSerializer 序列化器给 RPC 组件使用
        ProtobufMsgFactory.load();
    }

    /**
     * Rpc通信的客户端
     */
    protected volatile RpcClient rpcClient;
    /**
     * 业务线程池
     */
    protected ThreadPoolExecutor rpcExecutor;
    /**
     * Rpc远程调用的配置参数对象
     */
    protected RpcOptions rpcOptions;

    /**
     * 检查当前 ClientService 是否与对应节点建立了连接
     *
     * @param endpoint
     */
    @Override
    public boolean isConnected(Endpoint endpoint) {
        final RpcClient rc = this.rpcClient;
        return rc != null && isConnected(rc, endpoint);
    }

    private static boolean isConnected(final RpcClient rpcClient, final Endpoint endpoint) {
        return rpcClient.checkConnection(endpoint);
    }

    @Override
    public boolean checkConnection(Endpoint endpoint, boolean createIfAbsent) {
        if (this.rpcClient == null) {
            throw new IllegalStateException("Client service is uninitialized.");
        }
        return this.rpcClient.checkConnection(endpoint, createIfAbsent);
    }

    /**
     * 初始化一个客户端服务对象，它拥有 RpcClient
     * 1. 使用 RaftPrc 工厂创建一个客户端对象RpcClient
     * 2. 根据 RpcOptions 的配置初始化 RpcClient
     *
     * @param opts
     */
    @Override
    public synchronized boolean init(RpcOptions opts) {
        if (this.rpcClient != null) {
            return true;
        }
        this.rpcOptions = opts;
        return initRpcClient(this.rpcOptions.getRpcProcessorThreadPoolSize());
    }

    protected boolean initRpcClient(final int rpcProcessorThreadPoolSize) {
        // 获取负责创建客户端和服务端的RaftRpc工厂
        final RaftRpcFactory factory = RpcFactoryHelper.rpcFactory();
        // 使用 RaftRpc 工厂创建一个 BoltRpcClient
        this.rpcClient = factory.createRpcClient(factory.defaultJRaftClientConfigHelper(this.rpcOptions));
        configRpcClient(this.rpcClient);
        // 创建之后给它初始化
        this.rpcClient.init(this.rpcOptions);
        this.rpcExecutor = ThreadPoolUtil.newBuilder()
                .poolName("JRaft-RPC-Processor")
                // 是否开启性能检测
                .enableMetric(true)
                .coreThreads(rpcProcessorThreadPoolSize / 3)
                .keepAliveSeconds(60L)
                .workQueue(new ArrayBlockingQueue<>(10000))
                .threadFactory(new NamedThreadFactory("JRaft-Rpc-Processor-", true))
                .build();
        if (this.rpcOptions.getMetricRegistry() != null) {
            this.rpcOptions.getMetricRegistry().register("raft-rpc-client-thread-pool", new ThreadPoolMetricSet(this.rpcExecutor));
        }
        return true;
    }


    @Override
    public void shutdown() {
        if (this.rpcClient != null) {
            this.rpcClient.shutdown();
            this.rpcClient = null;
            this.rpcExecutor.shutdown();
        }
    }

    /**
     * 和指定的节点建立连接
     *
     * @param endpoint
     */
    @Override
    public boolean connect(Endpoint endpoint) {
        final RpcClient rpcClient = this.rpcClient;
        if (rpcClient == null) {
            throw new IllegalStateException("Client service is uninitialized.");
        }
        if (isConnected(endpoint)) {
            return true;
        }
        try {
            RpcRequests.PingRequest request = RpcRequests.PingRequest.newBuilder()
                    .setSendTimestamp(System.currentTimeMillis())
                    .build();
            final RpcRequests.ErrorResponse response = (RpcRequests.ErrorResponse) rpcClient.invokeSync(endpoint, request, this.rpcOptions.getRpcConnectTimeoutMs());
            return response.getErrorCode() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (com.alipay.remoting.exception.RemotingException e) {
            logger.error("Fail to connect {}, remoting exception: {}", endpoint.toString(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean disconnect(Endpoint endpoint) {
        final RpcClient rpcClient = this.rpcClient;
        if (rpcClient == null) {
            return true;
        }
        logger.info("Disconnect from {}.", endpoint.toString());
        rpcClient.closeConnection(endpoint);
        return true;
    }

    @Override
    public <T extends Message> Future<Message> invokeWithDone(final Endpoint endpoint, final Message request,
                                                              final RpcResponseClosure<T> done, final int timeoutMs) {
        return invokeWithDone(endpoint, request, done, timeoutMs, this.rpcExecutor);
    }

    public <T extends Message> Future<Message> invokeWithDone(final Endpoint endpoint, final Message request,
                                                              final RpcResponseClosure<T> done, final int timeoutMs,
                                                              final Executor rpcExecutor) {
        return invokeWithDone(endpoint, request, null, done, timeoutMs, rpcExecutor);
    }

    public <T extends Message> Future<Message> invokeWithDone(final Endpoint endpoint, final Message request,
                                                              final InvokeContext ctx,
                                                              final RpcResponseClosure<T> done, final int timeoutMs) {
        return invokeWithDone(endpoint, request, ctx, done, timeoutMs, this.rpcExecutor);
    }

    /**
     * 异步发送请求，接收到响应之后执行回调函数
     */
    public <T extends Message> Future<Message> invokeWithDone(final Endpoint endpoint,
                                                              final Message request,
                                                              final InvokeContext ctx,
                                                              final RpcResponseClosure<T> done,
                                                              final int timeoutMs,
                                                              final Executor rpcExecutor) {
        final RpcClient rpcClient = this.rpcClient;
        final FutureImpl<Message> future = new FutureImpl<>();
        final Executor currExecutor = rpcExecutor != null ? rpcExecutor : this.rpcExecutor;
        try {
            if (rpcClient == null) {
                future.failure(new IllegalStateException("Client service is uninitialized."));
                RpcUtils.runClosureInExecutor(currExecutor, done, new Status(RaftError.EINTERNAL, "Client service is uninitialized."));
            }
            // 异步调用，接收响应后执行回调方法
            rpcClient.invokeAsync(endpoint, request, ctx, new InvokeCallback() {
                @Override
                public void complete(Object result, Throwable error) {
                    if (future.isCancelled()) {
                        onCanceled(request, done);
                        return;
                    }
                    if (error == null) {
                        Status status = Status.OK();
                        Message msg;
                        // 如果返回的结果是 ErrorResponse ，说明执行失败
                        if (result instanceof RpcRequests.ErrorResponse) {
                            status = handleErrorResponse((RpcRequests.ErrorResponse) result);
                            msg = (Message) result;
                        } else if (result instanceof Message) {
                            final Descriptors.FieldDescriptor fd = ((Message) result).getDescriptorForType()
                                    .findFieldByNumber(RpcResponseFactory.ERROR_RESPONSE_NUM);
                            if (fd != null && ((Message) result).hasField(fd)) {
                                final RpcRequests.ErrorResponse eResp = (RpcRequests.ErrorResponse) ((Message) result).getField(fd);
                                status = handleErrorResponse(eResp);
                                msg = eResp;
                            } else {
                                msg = (T) result;
                            }
                        } else {
                            msg = (T) result;
                        }
                        if (done != null) {
                            try {
                                if (status.isOk()) {
                                    done.setResponse((T)msg);
                                }
                                done.run(status);
                            } catch (final Throwable t) {
                                logger.error("Fail to run RpcResponseClosure, the request id {}", result, t);
                            }
                        }
                        if (!future.isDone()) {
                            future.setResult(msg);
                        }

                    } else {
                        if (done != null) {
                            try {
                                done.run(new Status(error instanceof InvokeTimeoutException ? RaftError.ETIMEDOUT
                                        : RaftError.EINTERNAL, "RPC exception:" + error.getMessage()));
                            } catch (final Throwable t) {
                                logger.error("Fail to run RpcResponseClosure, the request is {}.", request, t);
                            }
                        }
                        if (!future.isDone()) {
                            future.failure(error);
                        }
                    }
                }
                @Override
                public Executor executor() {
                    return currExecutor;
                }
            }, timeoutMs <= 0 ? this.rpcOptions.getRpcDefaultTimeout() : timeoutMs);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            future.failure(e);
            // should be in another thread to avoid dead locking.
            RpcUtils.runClosureInExecutor(currExecutor, done,
                    new Status(RaftError.EINTR, "Sending rpc was interrupted"));
        } catch (final com.alipay.remoting.exception.RemotingException e) {
            future.failure(e);
            // should be in another thread to avoid dead locking.
            RpcUtils.runClosureInExecutor(currExecutor, done, new Status(RaftError.EINTERNAL,
                    "Fail to send a RPC request:" + e.getMessage()));

        }
        return future;
    }
    private static Status handleErrorResponse(final RpcRequests.ErrorResponse eResp) {
        final Status status = new Status();
        status.setCode(eResp.getErrorCode());
        if (eResp.hasErrorMsg()) {
            status.setErrorMsg(eResp.getErrorMsg());
        }
        return status;
    }
    private <T extends Message> void onCanceled(final Message request, final RpcResponseClosure<T> done) {
        if (done != null) {
            try {
                done.run(new Status(RaftError.ECANCELED, "RPC request was canceled by future."));
            } catch (final Throwable t) {
                logger.error("Fail to run RpcResponseClosure, the request is {}.", request, t);
            }
        }
    }

    protected void configRpcClient(final RpcClient rpcClient) {
    }

    public RpcClient getRpcClient() {
        return this.rpcClient;
    }
}
