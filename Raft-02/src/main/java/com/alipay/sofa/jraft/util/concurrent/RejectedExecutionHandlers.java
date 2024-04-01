package com.alipay.sofa.jraft.util.concurrent;

import java.util.concurrent.RejectedExecutionException;


/**
 * 拒绝策略实现类
 */
public final class RejectedExecutionHandlers {

    private static final RejectedExecutionHandler REJECT = (task, executor) -> {
        throw new RejectedExecutionException();
    };


    public static RejectedExecutionHandler reject() {
        return REJECT;
    }

    private RejectedExecutionHandlers() {
    }
}