package com.alipay.sofa.jraft.util;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * 线程池创建工厂
 */
public class ThreadPoolsFactory {
    private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolsFactory.class);
    /**
     * It is used to handle global closure tasks
     */
    private static final ConcurrentMap<String, ThreadPoolExecutor> GROUP_THREAD_POOLS = new ConcurrentHashMap<>();

    private static class GlobalThreadPoolHolder {
        private static final ThreadPoolExecutor INSTANCE = ThreadPoolUtil
                .newBuilder()
                .poolName("JRAFT_GROUP_DEFAULT_EXECUTOR")
                .enableMetric(true)
                .coreThreads(Utils.MIN_CLOSURE_EXECUTOR_POOL_SIZE)
                .maximumThreads(Utils.MAX_CLOSURE_EXECUTOR_POOL_SIZE)
                .keepAliveSeconds(60L)
                .workQueue(new SynchronousQueue<>())
                .threadFactory(
                        new NamedThreadFactory(
                                "JRaft-Group-Default-Executor-", true)).build();
    }

    /**
     * You can specify the ThreadPoolExecutor yourself here
     *
     * @param groupId  Raft-Group
     * @param executor To specify ThreadPoolExecutor
     */
    //一个集群组对应一个线程池
    public static void registerThreadPool(String groupId, ThreadPoolExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }

        if (GROUP_THREAD_POOLS.putIfAbsent(groupId, executor) != null) {
            throw new IllegalArgumentException(String.format("The group: %s has already registered the ThreadPool",
                    groupId));
        }
    }


    /**
     * Run a task in thread pool,returns the future object.
     */
    public static Future<?> runInThread(String groupId, final Runnable runnable) {
        //获得对应的value，也就是集群组对应的线程池，然后执行这个任务
        return GROUP_THREAD_POOLS.getOrDefault(groupId, GlobalThreadPoolHolder.INSTANCE).submit(runnable);
    }

    /**
     * Run closure with status in thread pool.
     */
    public static Future<?> runClosureInThread(String groupId, final Closure done, final Status status) {
        if (done == null) {
            return null;
        }
        return runInThread(groupId, () -> {
            try {
                done.run(status);
            } catch (final Throwable t) {
                LOG.error("Fail to run done closure", t);
            }
        });
    }

    /**
     * Run closure with OK status in thread pool.
     */
    public static Future<?> runClosureInThread(String groupId, final Closure done) {
        if (done == null) {
            return null;
        }
        return runClosureInThread(groupId, done, Status.OK());
    }
}
