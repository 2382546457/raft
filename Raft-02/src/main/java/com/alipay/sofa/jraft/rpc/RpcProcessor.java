package com.alipay.sofa.jraft.rpc;

import java.util.concurrent.Executor;

/**
 * @author : 小何
 * @Description : 请求处理器，负责处理T类型的消息
 * @date : 2024-04-04 13:36
 */
public interface RpcProcessor<T> {
    /**
     * 处理请求
     * @param context 此次请求的上下文，内含连接、对方IP+PORT
     * @param request 此次请求的数据
     */
    public void handleRequest(final RpcContext context, final T request);

    /**
     * 这个处理器关心的类型，返回类的全限定类名
     * @return
     */
    String interest();

    /**
     * 获取负责执行这个处理器的线程池
     * @return
     */
    default Executor executor() {
        return null;
    }

    /**
     * 获取线程池选择器
     * @return
     */
    default ExecutorSelector executorSelector() {
        return null;
    }
    interface ExecutorSelector {
        /**
         * 选择一个线程池去执行
         * @param reqClass
         * @param reqHandler
         * @return
         */
        Executor select(final String reqClass, final Object reqHandler);
    }
}
