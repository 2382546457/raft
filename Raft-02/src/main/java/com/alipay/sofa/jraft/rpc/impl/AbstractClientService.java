package com.alipay.sofa.jraft.rpc.impl;

import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.InvokeTimeoutException;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RemotingException;
import com.alipay.sofa.jraft.option.RpcOptions;
import com.alipay.sofa.jraft.rpc.*;
import com.alipay.sofa.jraft.rpc.closure.RpcResponseClosure;
import com.alipay.sofa.jraft.util.*;
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
 * @Description : 客户端服务抽象类，实现了 通用ClientService 中的所有方法
 * @date : 2024-04-04 16:27
 */
public abstract class AbstractClientService implements ClientService {
    private static final Logger logger = LoggerFactory.getLogger(AbstractClientService.class);

    static {
        // 加载Protobuf描述文件，并且注册ProtobufSerializer序列化器给RPC组件使用
        ProtobufMsgFactory.load();
    }

    protected volatile RpcClient rpcClient;
    protected ThreadPoolExecutor rpcExecutor;
    protected RpcOptions rpcOptions;

    @Override
    public boolean isConnected(final Endpoint endpoint) {
        final RpcClient rc = this.rpcClient;
        return rc != null && isConnected(rc, endpoint);
    }

    private static boolean isConnected(final RpcClient rpcClient, final Endpoint endpoint) {
        return rpcClient.checkConnection(endpoint);
    }


    @Override
    public boolean checkConnection(final Endpoint endpoint, final boolean createIfAbsent) {
        final RpcClient rc = this.rpcClient;
        if (rc == null) {
            throw new IllegalStateException("Client service is uninitialized.");
        }
        return rc.checkConnection(endpoint, createIfAbsent);
    }


    /**
     * 初始化客户端服务，其实就是根据 RpcOptions 创建 RpcClient
     * @param opts
     * @return
     */
    @Override
    public synchronized boolean init(RpcOptions opts) {
        if (this.rpcClient != null) {
            return true;
        }
        this.rpcOptions = rpcOptions;
        return initRpcClient(this.rpcOptions.getRpcProcessorThreadPoolSize());
    }
    protected void configRpcClient(final RpcClient rpcClient) {
    }
    protected boolean initRpcClient(int rpcProcessorThreadPoolSize) {
        // 拿到创建客户端和服务端的工厂，根据工程去创建客户端，并且给客户端初始化
        RaftRpcFactory factory = RpcFactoryHelper.rpcFactory();
        this.rpcClient = factory.createRpcClient(factory.defaultJRaftClientConfigHelper(this.rpcOptions));
        configRpcClient(this.rpcClient);
        this.rpcClient.init(this.rpcOptions);

        this.rpcExecutor = ThreadPoolUtil.newBuilder()
                //设置名字
                .poolName("JRaft-RPC-Processor")
                //是否开启性能检测
                .enableMetric(true)
                //设置核心线程数
                .coreThreads(rpcProcessorThreadPoolSize / 3)
                //设置最大线程数量
                .maximumThreads(rpcProcessorThreadPoolSize)
                //空闲线程存活时间
                .keepAliveSeconds(60L)
                //任务队列
                .workQueue(new ArrayBlockingQueue<>(10000))
                //线程工厂
                .threadFactory(new NamedThreadFactory("JRaft-RPC-Processor-", true))
                .build();
        if (this.rpcOptions.getMetricRegistry() != null) {
            this.rpcOptions.getMetricRegistry().register("raft-rpc-client-thread-pool",
                    new ThreadPoolMetricSet(this.rpcExecutor));
        }
        return true;
    }


    /**
     * 关闭客户端
     */
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
     * @param endpoint
     * @return
     */
    @Override
    public boolean connect(Endpoint endpoint) {
        RpcClient rc = this.rpcClient;
        if (rc == null) {
            throw new IllegalStateException("Client service is uninitialized.");
        }
        if (isConnected(endpoint)) {
            return true;
        }
        try {
            final RpcRequests.PingRequest req = RpcRequests.PingRequest.newBuilder()
                    .setSendTimestamp(System.currentTimeMillis())
                    .build();
            // 发送ping连接，同步等待连接结果
            RpcRequests.ErrorResponse response = (RpcRequests.ErrorResponse) rc.invokeSync(endpoint, req, this.rpcOptions.getRpcConnectTimeoutMs());
            return response.getErrorCode() == 0;
        } catch (Exception e) {
            logger.error("Fail to connect {}, remoting exception: {}.", endpoint, e.getMessage());
            return false;
        }
    }

    /**
     * 和指定的节点断开连接
     * @param endpoint
     * @return
     */
    @Override
    public boolean disconnect(final Endpoint endpoint) {
        final RpcClient rc = this.rpcClient;
        if (rc == null) {
            return true;
        }
        logger.info("Disconnect from {}.", endpoint);
        rc.closeConnection(endpoint);
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
     * 异步发送请求。当执行结束时触发 RpcResponseClosure
     * 最终还是调用 RpcClient 的异步发送
     * @param endpoint
     * @param request
     * @param ctx
     * @param done
     * @param timeoutMs
     * @param rpcExecutor
     * @return
     * @param <T>
     */
    public <T extends Message> Future<Message> invokeWithDone(final Endpoint endpoint, final Message request,
                                                              final InvokeContext ctx,
                                                              final RpcResponseClosure<T> done, final int timeoutMs,
                                                              final Executor rpcExecutor) {
        final RpcClient rc = this.rpcClient;
        final FutureImpl<Message> future = new FutureImpl<>();
        Executor currExecutor = rpcExecutor == null ? this.rpcExecutor : rpcExecutor;
        try {
            // 如果客户端为空，说明还没有初始化客户端
            if (rc == null) {
                future.failure(new IllegalStateException("Client service is uninitialized."));
                // 执行回调
                RpcUtils.runClosureInExecutor(currExecutor, done, new Status(RaftError.EINTERNAL, "Client service is uninitialized."));
                return future;
            }
            // 最终还是调用 RpcClient 的异步发送，当接收到请求时执行 InvokeCallback
            rc.invokeAsync(endpoint, request, ctx, new InvokeCallback() {
                @Override
                public void complete(Object result, Throwable err) {
                    // 处理回调的逻辑
                    onComplete(request, err, done, request, future);
                }
            }, timeoutMs <= 0 ? this.rpcOptions.getRpcDefaultTimeout() : timeoutMs);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            future.failure(e);
            // should be in another thread to avoid dead locking.
            RpcUtils.runClosureInExecutor(currExecutor, done,
                    new Status(RaftError.EINTR, "Sending rpc was interrupted"));
        } catch (final RemotingException e) {
            future.failure(e);
            // should be in another thread to avoid dead locking.
            RpcUtils.runClosureInExecutor(currExecutor, done, new Status(RaftError.EINTERNAL,
                    "Fail to send a RPC request:" + e.getMessage()));

        }
        return future;
    }

    /**
     * 从返回的响应RpcRequests.ErrorResponse中拿到 code 和 message 封装为 Status
     * @param resp
     * @return
     */
    private static Status handleErrorResponse(final RpcRequests.ErrorResponse resp) {
        final Status status = new Status();
        status.setCode(resp.getErrorCode());
        if (resp.hasErrorMsg()) {
            status.setErrorMsg(resp.getErrorMsg());
        }
        return status;
    }
    private <T extends Message> void onComplete(Object result, Throwable error, RpcResponseClosure<T> done, Message request, FutureImpl<Message> future) {
        // 如果出现异常, 当回调不为空时，执行回调。查看异步任务是否执行结束了，没执行结束将其置为失败
        if (error != null) {
            if (done != null) {
                try {
                    done.run(new Status(error instanceof InvokeTimeoutException ? RaftError.ETIMEDOUT : RaftError.EINTERNAL, "RPC exception:" + error.getMessage()));
                } catch (final Throwable t) {
                    logger.error("Failt to run RpcResponseClosure, the request is {}.", request);
                }
            }
            if (!future.isDone()) {
                future.failure(error);
            }
        } else {
            // 没有出现异常
            Status status = Status.OK();
            Message msg;
            if (result instanceof RpcRequests.ErrorResponse) {
                status = handleErrorResponse((RpcRequests.ErrorResponse) request);
                msg = (Message)result;
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
            // 执行回调，设置结果
            if (done != null) {
                try {
                    if (status.isOk()) {
                        done.setResponse((T) msg);
                    }
                    done.run(status);
                } catch (final Throwable t) {
                    logger.error("Fail to run RpcResponseClosure, the request is {}.", request, t);
                }
            }
            if (!future.isDone()) {
                future.setResult(msg);
            }

        }

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
    public RpcClient getRpcClient() {
        return this.rpcClient;
    }

}
