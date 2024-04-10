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

/**
 * @author : 小何
 * @Description : Rpc工具类
 * @date : 2024-04-04 14:02
 */
public final class RpcUtils {
    private static final Logger logger = LoggerFactory.getLogger(RpcUtils.class);

    public static final int MIN_RPC_CLOSURE_EXECUTOR_POOL_SIZE = SystemPropertyUtil.getInt(
            "jraft.rpc.closure.threadpool.size.min",
            Utils.cpus()
    );

    public static final int MAX_RPC_CLOSURE_EXECUTOR_POOL_SIZE = SystemPropertyUtil.getInt(
            "jraft.rpc.closure.threadpool.size.max",
            Math.max(100, Utils.cpus() * 5)
    );

    /**
     * 执行节点之间RPC请求与响应的回调方法的线程池
     */
    private static ThreadPoolExecutor RPC_CLOSURE_EXECUTOR = ThreadPoolUtil
            .newBuilder()
            .poolName("JRAFT_RPC_CLOSURE_EXECUTOR")
            .enableMetric(true)
            .coreThreads(MIN_RPC_CLOSURE_EXECUTOR_POOL_SIZE)
            .maximumThreads(MAX_RPC_CLOSURE_EXECUTOR_POOL_SIZE)
            .keepAliveSeconds(60L)
            .workQueue(new SynchronousQueue<>())
            .threadFactory(new NamedThreadFactory("JRAFT-RPC-CLOSURE-EXECUTOR-", true))
            .build();


    /**
     * 提交一个回调方法给默认线程池运行
     * @param done
     * @return
     */
    public static Future<?> runClosureInThread(final Closure done) {
        if (done == null) {
            return null;
        }
        return runClosureInThread(done, Status.OK());
    }

    public static Future<?> runInThread(final Runnable runnable) {
        return RPC_CLOSURE_EXECUTOR.submit(runnable);
    }

    public static Future<?> runClosureInThread(final Closure done, final Status status) {
        if (done == null) {
            return null;
        }
        return runInThread(() -> {
            try {
                done.run(status);
            } catch (final Throwable e) {
                logger.error("Fail to tun done closure.", e);
            }
        });
    }

    /**
     * 提交一个回调给指定线程池运行
     * @param executor
     * @param done
     * @param status
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
                logger.error("Fail to run done closure.", t);
            }
        });
    }

    private RpcUtils() {
    }
}
