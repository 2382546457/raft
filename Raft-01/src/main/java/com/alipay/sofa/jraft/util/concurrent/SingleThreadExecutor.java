package com.alipay.sofa.jraft.util.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public interface SingleThreadExecutor extends Executor {


    boolean shutdownGracefully();


    boolean shutdownGracefully(final long timeout, final TimeUnit unit);
}