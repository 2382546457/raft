package com.alipay.sofa.jraft.util.concurrent;

import java.util.concurrent.TimeUnit;

public interface FixedThreadsExecutorGroup extends Iterable<SingleThreadExecutor> {


    SingleThreadExecutor next();


    void execute(final int index, final Runnable task);


    boolean shutdownGracefully();

    boolean shutdownGracefully(final long timeout, final TimeUnit unit);
}