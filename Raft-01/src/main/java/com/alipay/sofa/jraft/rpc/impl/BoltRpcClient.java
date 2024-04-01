package com.alipay.sofa.jraft.rpc.impl;

import com.alipay.remoting.ConnectionEventType;
import com.alipay.remoting.RejectedExecutionPolicy;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.exception.RemotingException;
import com.alipay.sofa.jraft.ReplicatorGroup;
import com.alipay.sofa.jraft.option.RpcOptions;
import com.alipay.sofa.jraft.rpc.InvokeCallback;
import com.alipay.sofa.jraft.rpc.InvokeContext;
import com.alipay.sofa.jraft.rpc.RpcClient;
import com.alipay.sofa.jraft.rpc.impl.core.ClientServiceConnectionEventProcessor;
import com.alipay.sofa.jraft.util.Endpoint;
import com.alipay.sofa.jraft.util.Requires;
import com.alipay.sofa.jraft.error.InvokeTimeoutException;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-03-31 21:03
 */
public class BoltRpcClient implements RpcClient {
    public static final String BOLT_CTX = "BOLT_CTX";
    public static final String BOLT_REJECTED_EXECUTION_POLICY = "BOLT_REJECTED_EXECUTION_POLICY";

    private final com.alipay.remoting.rpc.RpcClient rpcClient;
    private RpcOptions opts;
    public BoltRpcClient(com.alipay.remoting.rpc.RpcClient rpcClient) {
        this.rpcClient = Requires.requireNonNull(rpcClient, "rpcClient");
    }

    @Override
    public boolean init(RpcOptions opts) {
        this.opts = opts;
        this.rpcClient.option(BoltClientOption.NETTY_FLUSH_CONSOLIDATION, true);
        this.rpcClient.initWriteBufferWaterMark(BoltRaftRpcFactory.CHANNEL_WRITE_BUF_LOW_WATER_MARK,
                BoltRaftRpcFactory.CHANNEL_WRITE_BUF_HIGH_WATER_MARK);
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
        return this.rpcClient.checkConnection(endpoint.toString(), createIfAbsent, true);
    }

    @Override
    public void registerConnectionEventListener(ReplicatorGroup replicatorGroup) {
        this.rpcClient.addConnectionEventProcessor(
                ConnectionEventType.CONNECT,
                new ClientServiceConnectionEventProcessor(replicatorGroup)
        );
    }

    @Override
    public void closeConnection(Endpoint endpoint) {
        Requires.requireNonNull(endpoint, "endpoint");
        this.rpcClient.closeConnection(endpoint.toString());
    }

    @Override
    public void invokeAsync(Endpoint endpoint, Object request, InvokeContext ctx, InvokeCallback callback, long timeoutMs) throws InterruptedException, RemotingException {
        Requires.requireNonNull(endpoint, "endpoint");
        this.rpcClient.invokeWithCallback(endpoint.toString(), request, getBoltInvokeCtx(ctx), getBoltCallback(callback, ctx), (int) timeoutMs);
    }

    @Override
    public Object invokeSync(Endpoint endpoint, Object request, InvokeContext ctx, long timeoutMs) throws InterruptedException, RemotingException, InvokeTimeoutException {
        Requires.requireNonNull(endpoint, "endpoint");
        return this.rpcClient.invokeSync(endpoint.toString(), request, getBoltInvokeCtx(ctx), (int) timeoutMs);
    }
    private BoltCallback getBoltCallback(final InvokeCallback callback, final InvokeContext ctx) {
        Requires.requireNonNull(callback, "callback");
        return new BoltCallback(callback, getRejectedPolicy(ctx));
    }
    private RejectedExecutionPolicy getRejectedPolicy(final InvokeContext ctx) {
        return ctx == null ? RejectedExecutionPolicy.CALLER_HANDLE_EXCEPTION : ctx.getOrDefault(
                BOLT_REJECTED_EXECUTION_POLICY, RejectedExecutionPolicy.CALLER_HANDLE_EXCEPTION);
    }
    public com.alipay.remoting.rpc.RpcClient getRpcClient() {
        return rpcClient;
    }

    private com.alipay.remoting.InvokeContext getBoltInvokeCtx(final InvokeContext ctx) {
        com.alipay.remoting.InvokeContext boltCtx;
        if (ctx == null) {
            boltCtx = new com.alipay.remoting.InvokeContext();
            boltCtx.put(com.alipay.remoting.InvokeContext.BOLT_CRC_SWITCH, this.opts.isEnableRpcChecksum());
            return boltCtx;
        }
        boltCtx = ctx.get(BOLT_CTX);
        if ( boltCtx != null) {
            return boltCtx;
        }
        boltCtx = new com.alipay.remoting.InvokeContext();
        for (Map.Entry<String, Object> entry : ctx.entrySet()) {
            boltCtx.put(entry.getKey(), entry.getValue());
        }
        final Boolean crcSwitch = ctx.get(InvokeContext.CRC_SWITCH);
        if (crcSwitch != null) {
            boltCtx.put(com.alipay.remoting.InvokeContext.BOLT_CRC_SWITCH, crcSwitch);
        }
        return boltCtx;
    }

    private static class BoltCallback implements com.alipay.remoting.RejectionProcessableInvokeCallback {
        private final InvokeCallback callback;
        private final RejectedExecutionPolicy rejectedExecutionPolicy;

        public BoltCallback(InvokeCallback callback, RejectedExecutionPolicy rejectedExecutionPolicy) {
            this.callback = callback;
            this.rejectedExecutionPolicy = rejectedExecutionPolicy;
        }

        @Override
        public RejectedExecutionPolicy rejectedExecutionPolicy() {
            return this.rejectedExecutionPolicy;
        }

        @Override
        public void onResponse(Object o) {
            this.callback.complete(o, null);
        }

        @Override
        public void onException(Throwable err) {
            this.callback.complete(null, err);
        }

        @Override
        public Executor getExecutor() {
            return null;
        }
    }
}
