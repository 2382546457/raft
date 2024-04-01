package com.alipay.sofa.jraft.util.concurrent;

import com.alipay.sofa.jraft.util.ExecutorServiceHelper;
import com.alipay.sofa.jraft.util.NamedThreadFactory;
import com.alipay.sofa.jraft.util.ThreadPoolUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


/**
 * 默认的单线程执行器，其实就是在这个对象内部创建了只管理一个线程的线程池
 */
public final class DefaultSingleThreadExecutor implements SingleThreadExecutor {

    private final SingleThreadExecutor singleThreadExecutor;

    /**
     * Anti-gentleman is not against villains, we believe that you are
     * providing a single-thread executor.
     *
     * @param singleThreadExecutorService a {@link ExecutorService} instance
     */
    public DefaultSingleThreadExecutor(ExecutorService singleThreadExecutorService) {
        this.singleThreadExecutor = wrapSingleThreadExecutor(singleThreadExecutorService);
    }

    public DefaultSingleThreadExecutor(String poolName, int maxPendingTasks) {
        this.singleThreadExecutor = createSingleThreadExecutor(poolName, maxPendingTasks);
    }

    @Override
    public void execute(final Runnable task) {
        this.singleThreadExecutor.execute(task);
    }

    @Override
    public boolean shutdownGracefully() {
        return this.singleThreadExecutor.shutdownGracefully();
    }

    @Override
    public boolean shutdownGracefully(final long timeout, final TimeUnit unit) {
        return this.singleThreadExecutor.shutdownGracefully(timeout, unit);
    }

    private static SingleThreadExecutor wrapSingleThreadExecutor(final ExecutorService executor) {
        if (executor instanceof SingleThreadExecutor) {
            return (SingleThreadExecutor) executor;
        } else {
            return new SingleThreadExecutor() {

                @Override
                public boolean shutdownGracefully() {
                    return ExecutorServiceHelper.shutdownAndAwaitTermination(executor);
                }

                @Override
                public boolean shutdownGracefully(final long timeout, final TimeUnit unit) {
                    return ExecutorServiceHelper.shutdownAndAwaitTermination(executor, unit.toMillis(timeout));
                }

                @Override
                public void execute(final Runnable command) {
                    executor.execute(command);
                }
            };
        }
    }

    private static SingleThreadExecutor createSingleThreadExecutor(final String poolName, final int maxPendingTasks) {
        final ExecutorService singleThreadPool = ThreadPoolUtil.newBuilder()
                .poolName(poolName)
                .enableMetric(true)
                .coreThreads(1)
                .maximumThreads(1)
                .keepAliveSeconds(60L)
                .workQueue(new LinkedBlockingQueue<>(maxPendingTasks))
                .threadFactory(new NamedThreadFactory(poolName, true))
                .build();

        return new SingleThreadExecutor() {

            @Override
            public boolean shutdownGracefully() {
                return ExecutorServiceHelper.shutdownAndAwaitTermination(singleThreadPool);
            }

            @Override
            public boolean shutdownGracefully(final long timeout, final TimeUnit unit) {
                return ExecutorServiceHelper.shutdownAndAwaitTermination(singleThreadPool, unit.toMillis(timeout));
            }

            @Override
            public void execute(final Runnable command) {
                singleThreadPool.execute(command);
            }
        };
    }
}
