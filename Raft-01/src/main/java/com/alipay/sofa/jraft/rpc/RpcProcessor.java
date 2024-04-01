package com.alipay.sofa.jraft.rpc;

import java.util.concurrent.Executor;

/**
 * @author : 小何
 * @Description : 处理器，用户想要处理 User 类型的数据，就实现一个 UserProcessor<User>
 * @date : 2024-03-31 14:37
 */
public interface RpcProcessor<T> {
    public void handleRequest(final RpcContext rpcContext, final T request);

    /**
     * 这个Processor感兴趣的类型
     * @return
     */
    public String interest();

    /**
     * 这个处理器的线程池
     * @return
     */
    default Executor executor() {
        return null;
    }

    default ExecutorSelector executorSelector() {
        return null;
    }
    interface ExecutorSelector {
        Executor select(final String reqClass, final Object reqHeader);
    }
}
