package com.alipay.sofa.jraft.rpc.impl;

import com.alipay.remoting.ConnectionEventType;
import com.alipay.remoting.RejectedExecutionPolicy;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.sofa.jraft.ReplicatorGroup;
import com.alipay.sofa.jraft.error.InvokeTimeoutException;
import com.alipay.sofa.jraft.error.RemotingException;
import com.alipay.sofa.jraft.option.RpcOptions;
import com.alipay.sofa.jraft.rpc.InvokeCallback;
import com.alipay.sofa.jraft.rpc.InvokeContext;
import com.alipay.sofa.jraft.rpc.RpcClient;
import com.alipay.sofa.jraft.rpc.impl.core.ClientServiceConnectionEventProcessor;
import com.alipay.sofa.jraft.util.Endpoint;
import com.alipay.sofa.jraft.util.Requires;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @author : 小何
 * @Description : 自己封装的BoltRpcClient，其实内部是 com.alipay.remoting.rpc.RpcClient
 * @date : 2024-04-04 17:30
 */
public class BoltRpcClient implements RpcClient {
    public static final String BOLT_CTX = "BOLT_CTX";
    public static final String BOLT_REJECTED_EXECUTION_POLICY = "BOLT_REJECTED_EXECUTION_POLICY";

    private final com.alipay.remoting.rpc.RpcClient rpcClient;
    private RpcOptions opts;

    public BoltRpcClient(com.alipay.remoting.rpc.RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @Override
    public boolean init(RpcOptions opts) {
        this.opts = opts;
        this.rpcClient.option(BoltClientOption.NETTY_FLUSH_CONSOLIDATION, true);
        this.rpcClient.initWriteBufferWaterMark(BoltRaftRpcFactory.CHANNEL_WRITE_BUF_LOW_WATER_MARK, BoltRaftRpcFactory.CHANNEL_WRITE_BUF_HIGH_WATER_MARK);
        this.rpcClient.enableReconnectSwitch();
        this.rpcClient.startup();
        return true;
    }

    @Override
    public void shutdown() {
        this.rpcClient.shutdown();
    }

    @Override
    public boolean checkConnection(Endpoint endpoint) {
        Requires.requireNonNull(endpoint, "endpoint");
        return this.rpcClient.checkConnection(endpoint.toString());
    }

    @Override
    public boolean checkConnection(Endpoint endpoint, boolean createIfAbsent) {
        Requires.requireNonNull(endpoint, "endpoint");
        return this.rpcClient.checkConnection(endpoint.toString(), createIfAbsent);
    }

    @Override
    public void closeConnection(Endpoint endpoint) {
        Requires.requireNonNull(endpoint, "endpoint");
        this.rpcClient.closeConnection(endpoint.toString());
    }

    @Override
    public void registerConnectionEventListener(ReplicatorGroup replicatorGroup) {
        // ClientServiceConnectionEventProcessor 是 ConnectionEventProcessor的子类
        this.rpcClient.addConnectionEventProcessor(ConnectionEventType.CONNECT, new ClientServiceConnectionEventProcessor(replicatorGroup));
    }


    /**
     * 同步调用
     * @param endpoint
     * @param request
     * @param ctx
     * @param timeoutMs
     * @return
     * @throws InterruptedException
     * @throws RemotingException
     */
    @Override
    public Object invokeSync(Endpoint endpoint, Object request, InvokeContext ctx, long timeoutMs) throws InterruptedException, RemotingException {
        Requires.requireNonNull(endpoint, "endpoint");
        try {
            // 调用 Sofa-Bolt 的 invokeSync 方法发送请求
            return this.rpcClient.invokeSync(endpoint.toString(), request, getBoltInvokeCtx(ctx), (int)timeoutMs);
        } catch (final com.alipay.remoting.rpc.exception.InvokeTimeoutException e) {
            throw new InvokeTimeoutException(e);
        } catch (final com.alipay.remoting.exception.RemotingException e) {
            throw new RemotingException(e);
        }
    }

    /**
     * 异步调用
     * @param endpoint
     * @param request
     * @param ctx
     * @param callback
     * @param timeoutMs
     * @throws InterruptedException
     * @throws RemotingException
     */
    @Override
    public void invokeAsync(Endpoint endpoint, Object request, InvokeContext ctx, InvokeCallback callback, long timeoutMs) throws InterruptedException, RemotingException {
        Requires.requireNonNull(endpoint, "endpoint");
        try {
            this.rpcClient.invokeWithCallback(endpoint.toString(), request, getBoltInvokeCtx(ctx), getBoltCallback(callback, ctx), (int)timeoutMs);
        } catch (final com.alipay.remoting.rpc.exception.InvokeTimeoutException e) {
            throw new InvokeTimeoutException(e);
        } catch (final com.alipay.remoting.exception.RemotingException e) {
            throw new RemotingException(e);
        }
    }

    /**
     * 将我们的 InvokeContext 封装为 Bolt 的 InvokeContext。
     * Context 中有Map，只需要将Map中的值转移一下。
     * @param invokeContext
     * @return
     */
    private com.alipay.remoting.InvokeContext getBoltInvokeCtx(final InvokeContext invokeContext) {
        com.alipay.remoting.InvokeContext context;
        if (invokeContext == null) {
            context = new com.alipay.remoting.InvokeContext();
            context.put(com.alipay.remoting.InvokeContext.BOLT_CRC_SWITCH, this.opts.isEnableRpcChecksum());
            return context;
        }
        context = invokeContext.get(BOLT_CTX);
        if (context != null) {
            return context;
        }
        context = new com.alipay.remoting.InvokeContext();
        for (Map.Entry<String, Object> entry : invokeContext.entrySet()) {
            context.put(entry.getKey(), entry.getValue());
        }
        final Boolean crcSwitch = invokeContext.get(InvokeContext.CRC_SWITCH);
        if(crcSwitch != null) {
            context.put(com.alipay.remoting.InvokeContext.BOLT_CRC_SWITCH, crcSwitch);
        }
        return context;
    }
    private BoltCallback getBoltCallback(final InvokeCallback callback, final InvokeContext ctx) {
        Requires.requireNonNull(callback, "callback");
        return new BoltCallback(callback, getRejectedPolicy(ctx));
    }
    private RejectedExecutionPolicy getRejectedPolicy(final InvokeContext ctx) {
        return ctx == null ? RejectedExecutionPolicy.CALLER_HANDLE_EXCEPTION : ctx.getOrDefault(
                BOLT_REJECTED_EXECUTION_POLICY, RejectedExecutionPolicy.CALLER_HANDLE_EXCEPTION);
    }
    private static class BoltCallback implements com.alipay.remoting.RejectionProcessableInvokeCallback {

        private final InvokeCallback          callback;
        private final RejectedExecutionPolicy rejectedPolicy;

        private BoltCallback(final InvokeCallback callback, final RejectedExecutionPolicy rejectedPolicy) {
            this.callback = callback;
            this.rejectedPolicy = rejectedPolicy;
        }

        @Override
        public void onResponse(final Object result) {
            this.callback.complete(result, null);
        }

        @Override
        public void onException(final Throwable err) {
            this.callback.complete(null, err);
        }

        @Override
        public Executor getExecutor() {
            return this.callback.executor();
        }

        @Override
        public RejectedExecutionPolicy rejectedExecutionPolicy() {
            return this.rejectedPolicy;
        }
    }
}
