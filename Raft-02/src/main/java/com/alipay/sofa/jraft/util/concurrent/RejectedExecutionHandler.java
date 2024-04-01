package com.alipay.sofa.jraft.util.concurrent;


//拒绝策略接口
public interface RejectedExecutionHandler {

    void rejected(final Runnable task, final SingleThreadExecutor executor);
}
