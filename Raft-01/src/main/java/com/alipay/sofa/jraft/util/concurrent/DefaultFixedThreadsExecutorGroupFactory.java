package com.alipay.sofa.jraft.util.concurrent;


import com.alipay.remoting.NamedThreadFactory;
import com.alipay.sofa.jraft.util.Requires;
import com.alipay.sofa.jraft.util.Utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;


/**
 * 创建执行器组的工厂
 */
public final class DefaultFixedThreadsExecutorGroupFactory implements FixedThreadsExecutorGroupFactory {

    public static final DefaultFixedThreadsExecutorGroupFactory INSTANCE = new DefaultFixedThreadsExecutorGroupFactory();

    @Override
    public FixedThreadsExecutorGroup newExecutorGroup(final int nThreads, final String poolName,
                                                      final int maxPendingTasksPerThread) {
        return newExecutorGroup(nThreads, poolName, maxPendingTasksPerThread, false);
    }

    @Override
    public FixedThreadsExecutorGroup newExecutorGroup(final int nThreads, final String poolName,
                                                      final int maxPendingTasksPerThread, final boolean useMpscQueue) {
        Requires.requireTrue(nThreads > 0, "nThreads must > 0");
        final boolean mpsc = useMpscQueue && Utils.USE_MPSC_SINGLE_THREAD_EXECUTOR;
        final SingleThreadExecutor[] children = new SingleThreadExecutor[nThreads];
        final ThreadFactory threadFactory = mpsc ? new NamedThreadFactory(poolName, true) : null;
        for (int i = 0; i < nThreads; i++) {
            if (mpsc) {
                children[i] = new MpscSingleThreadExecutor(maxPendingTasksPerThread, threadFactory);
            } else {
                children[i] = new DefaultSingleThreadExecutor(poolName, maxPendingTasksPerThread);
            }
        }
        return new DefaultFixedThreadsExecutorGroup(children);
    }

    @Override
    public FixedThreadsExecutorGroup newExecutorGroup(final SingleThreadExecutor[] children) {
        return new DefaultFixedThreadsExecutorGroup(children);
    }

    @Override
    public FixedThreadsExecutorGroup newExecutorGroup(final SingleThreadExecutor[] children,
                                                      final ExecutorChooserFactory.ExecutorChooser chooser) {
        return new DefaultFixedThreadsExecutorGroup(children, chooser);
    }

    @Override
    public FixedThreadsExecutorGroup newExecutorGroup(final ExecutorService[] children) {
        return new DefaultFixedThreadsExecutorGroup(children);
    }

    @Override
    public FixedThreadsExecutorGroup newExecutorGroup(final ExecutorService[] children,
                                                      final ExecutorChooserFactory.ExecutorChooser chooser) {
        return new DefaultFixedThreadsExecutorGroup(children, chooser);
    }

    private DefaultFixedThreadsExecutorGroupFactory() {
    }
}
