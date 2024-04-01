package com.alipay.sofa.jraft.util.concurrent;

import java.util.concurrent.ExecutorService;


/**
 * 创建执行器组工厂类要实现的接口
 */
public interface FixedThreadsExecutorGroupFactory {

    FixedThreadsExecutorGroup newExecutorGroup(final int nThreads, final String poolName,
                                               final int maxPendingTasksPerThread);

    FixedThreadsExecutorGroup newExecutorGroup(final int nThreads, final String poolName,
                                               final int maxPendingTasksPerThread, final boolean useMpscQueue);

    FixedThreadsExecutorGroup newExecutorGroup(final SingleThreadExecutor[] children);

    FixedThreadsExecutorGroup newExecutorGroup(final SingleThreadExecutor[] children,
                                               final ExecutorChooserFactory.ExecutorChooser chooser);

    FixedThreadsExecutorGroup newExecutorGroup(final ExecutorService[] children);

    FixedThreadsExecutorGroup newExecutorGroup(final ExecutorService[] children,
                                               final ExecutorChooserFactory.ExecutorChooser chooser);

}
