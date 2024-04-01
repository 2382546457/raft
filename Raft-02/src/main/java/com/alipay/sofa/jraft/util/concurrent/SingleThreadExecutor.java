package com.alipay.sofa.jraft.util.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;


//单线程执行器接口
public interface SingleThreadExecutor extends Executor {


    boolean shutdownGracefully();


    boolean shutdownGracefully(final long timeout, final TimeUnit unit);
}