package com.alipay.sofa.jraft.util;

import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.util.timer.RaftTimerFactory;
import org.apache.commons.lang.StringUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;

public final class JRaftUtils {

    private final static RaftTimerFactory TIMER_FACTORY = JRaftServiceLoader.load(RaftTimerFactory.class).first();

    //返回定时器工厂
    public static RaftTimerFactory raftTimerFactory() {
        return TIMER_FACTORY;
    }
    
    //创建一个执行器，这里创建的其实就是一个线程池
    public static Executor createExecutor(final String prefix, final int number) {
        if (number <= 0) {
            return null;
        }
        return ThreadPoolUtil.newBuilder()
                .poolName(prefix)
                .enableMetric(true)
                .coreThreads(number)
                .maximumThreads(number)
                .keepAliveSeconds(60L)
                .workQueue(new SynchronousQueue<>())
                .threadFactory(createThreadFactory(prefix))
                .build();
    }

    /**
     * Create a thread factory.
     *
     * @param prefixName the prefix name of thread
     * @return a new {@link ThreadFactory} instance
     *
     * @since 0.0.3
     */
    public static ThreadFactory createThreadFactory(final String prefixName) {
        return new NamedThreadFactory(prefixName, true);
    }

    /**
     * Create a configuration from a string in the form of "host1:port1[:idx],host2:port2[:idx]......",
     * returns a empty configuration when string is blank.
     */
    public static Configuration getConfiguration(final String s) {
        final Configuration conf = new Configuration();
        if (StringUtils.isBlank(s)) {
            return conf;
        }
        if (conf.parse(s)) {
            return conf;
        }
        throw new IllegalArgumentException("Invalid conf str:" + s);
    }

    /**
     * Create a peer from a string in the form of "host:port[:idx]",
     * returns a empty peer when string is blank.
     */
    public static PeerId getPeerId(final String s) {
        final PeerId peer = new PeerId();
        if (StringUtils.isBlank(s)) {
            return peer;
        }
        if (peer.parse(s)) {
            return peer;
        }
        throw new IllegalArgumentException("Invalid peer str:" + s);
    }

    /**
     * Create a Endpoint instance from  a string in the form of "host:port",
     * returns null when string is blank.
     */
    public static Endpoint getEndPoint(final String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
        final String[] tmps = StringUtils.split(s, ':');
        if (tmps.length != 2) {
            throw new IllegalArgumentException("Invalid endpoint string: " + s);
        }
        return new Endpoint(tmps[0], Integer.parseInt(tmps[1]));
    }

    private JRaftUtils() {
    }
}