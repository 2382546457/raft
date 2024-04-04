package com.alipay.sofa.jraft.rpc.closure;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.rpc.RpcContext;
import com.alipay.sofa.jraft.util.RpcFactoryHelper;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author : 小何
 * @Description : 请求结束时的回调方法
 * @date : 2024-04-04 13:48
 */
public class RpcRequestClosure implements Closure {
    private static final Logger LOG = LoggerFactory
            .getLogger(RpcRequestClosure.class);

    private static final AtomicIntegerFieldUpdater<RpcRequestClosure> STATE_UPDATER = AtomicIntegerFieldUpdater
            .newUpdater(
                    RpcRequestClosure.class,
                    "state");
    private volatile int state = PENDING;
    private static final int PENDING = 0;
    private static final int RESPOND = 1;
    /**
     * 处理请求结束时可能发送响应，所以需要RPC上下文
     */
    private final RpcContext rpcCtx;
    /**
     * 默认响应
     */
    private final Message defaultResp;

    public RpcRequestClosure(RpcContext rpcCtx) {
        this(rpcCtx, null);
    }
    public RpcRequestClosure(RpcContext rpcCtx, Message defaultResp) {
        super();
        this.rpcCtx = rpcCtx;
        this.defaultResp = defaultResp;
    }
    @Override
    public void run(Status status) {
        sendResponse(RpcFactoryHelper.responseFactory().newResponse(this.defaultResp, status));
    }
    public void sendResponse(final Message msg) {
        if (!STATE_UPDATER.compareAndSet(this, PENDING, RESPOND)) {
            LOG.warn("A response: {} send repeatedly!", msg);
        }
        this.rpcCtx.sendResponse(msg);
    }
    public RpcContext getRpcCtx() {
        return rpcCtx;
    }
}
