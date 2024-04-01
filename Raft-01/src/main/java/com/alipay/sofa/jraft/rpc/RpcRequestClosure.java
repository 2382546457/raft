package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-03-31 18:29
 */
public class RpcRequestClosure implements Closure {
    private static final Logger logger = LoggerFactory.getLogger(RpcRequestClosure.class);
    private static final int PENDING = 0;
    private static final int RESPOND = 1;

    private final RpcContext rpcCtx;

    private final Message defaultResp;
    private volatile int state = PENDING;

    private static final AtomicIntegerFieldUpdater<RpcRequestClosure> STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(RpcRequestClosure.class, "state");

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
            logger.warn("A response : {} sent repeatedly!", msg);
            return;
        }
        this.rpcCtx.sendResponse(msg);
    }
    public RpcContext getRpcCtx() {
        return rpcCtx;
    }
}
