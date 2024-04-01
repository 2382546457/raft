package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.util.NamedThreadFactory;
import com.alipay.sofa.jraft.util.SystemPropertyUtil;
import com.alipay.sofa.jraft.util.ThreadPoolUtil;
import com.alipay.sofa.jraft.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;

public final class RpcUtils {

    private static final Logger LOG = LoggerFactory.getLogger(RpcUtils.class);

    /**
     * Default jraft closure executor pool minimum size, CPUs by default.
     */
    public static final int MIN_RPC_CLOSURE_EXECUTOR_POOL_SIZE = SystemPropertyUtil.getInt(
            "jraft.rpc.closure.threadpool.size.min",
            Utils.cpus());

    /**
     * Default jraft closure executor pool maximum size.
     */
    public static final int MAX_RPC_CLOSURE_EXECUTOR_POOL_SIZE = SystemPropertyUtil.getInt(
            "jraft.rpc.closure.threadpool.size.max",
            Math.max(100, Utils.cpus() * 5));


    //专门用于执行节点与节点之间RPC通信时回调方法的线程池
    //这里针对的主要是领导者向跟随着通信时的回调方法
    private static ThreadPoolExecutor RPC_CLOSURE_EXECUTOR = ThreadPoolUtil
            .newBuilder()
            .poolName("JRAFT_RPC_CLOSURE_EXECUTOR")
            .enableMetric(true)
            .coreThreads(MIN_RPC_CLOSURE_EXECUTOR_POOL_SIZE)
            .maximumThreads(MAX_RPC_CLOSURE_EXECUTOR_POOL_SIZE)
            .keepAliveSeconds(60L)
            .workQueue(new SynchronousQueue<>())
            .threadFactory(new NamedThreadFactory("JRaft-Rpc-Closure-Executor-", true)).build();

    /**
     * Run closure with OK status in thread pool.
     */
    public static Future<?> runClosureInThread(final Closure done) {
        if (done == null) {
            return null;
        }
        return runClosureInThread(done, Status.OK());
    }

    /**
     * Run a task in thread pool, returns the future object.
     */
    public static Future<?> runInThread(final Runnable runnable) {
        return RPC_CLOSURE_EXECUTOR.submit(runnable);
    }

    /**
     * Run closure with status in thread pool.
     */
    public static Future<?> runClosureInThread(final Closure done, final Status status) {
        if (done == null) {
            return null;
        }

        return runInThread(() -> {
            try {
                done.run(status);
            } catch (final Throwable t) {
                LOG.error("Fail to run done closure.", t);
            }
        });
    }

    /**
     * Run closure with status in specified executor
     */
    public static void runClosureInExecutor(final Executor executor, final Closure done, final Status status) {
        if (done == null) {
            return;
        }

        if (executor == null) {
            runClosureInThread(done, status);
            return;
        }

        executor.execute(() -> {
            try {
                done.run(status);
            } catch (final Throwable t) {
                LOG.error("Fail to run done closure.", t);
            }
        });
    }

    private RpcUtils() {
    }
}
