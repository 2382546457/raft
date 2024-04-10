package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.rpc.closure.RpcRequestClosure;
import com.alipay.sofa.jraft.util.RpcFactoryHelper;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * @author : 小何
 * @Description : 处理请求的Processor
 * @date : 2024-04-04 16:19
 */
public abstract class RpcRequestProcessor<T extends Message> implements RpcProcessor<T> {
    private static final Logger logger = LoggerFactory.getLogger(RpcRequestProcessor.class);

    private final Executor executor;
    private final Message defaultResp;

    /**
     * 处理请求的方法，由子类实现
     * @param request
     * @param done
     * @return
     */
    public abstract Message processRequest(final T request, final RpcRequestClosure done);

    public RpcRequestProcessor(Executor executor, Message defaultResp) {
        super();
        this.executor = executor;
        this.defaultResp = defaultResp;
    }

    /**
     * 调用子类的实现去处理请求，然后调用 RpcContext 返回 response
     * @param context 此次请求的上下文，内含连接、对方IP+PORT
     * @param request 此次请求的数据
     */
    @Override
    public void handleRequest(RpcContext context, T request) {
        try {
            final Message msg = processRequest(request, new RpcRequestClosure(context, this.defaultResp));
            if (msg != null) {
                context.sendResponse(msg);
            }
        } catch (final Throwable t) {
            logger.error("handleRequest {} failed", request, t);
            context.sendResponse(RpcFactoryHelper.responseFactory().newResponse(defaultResp(), -1, "handleRequest internal error"));
        }
    }


    public Message defaultResp() {
        return this.defaultResp;
    }
}
