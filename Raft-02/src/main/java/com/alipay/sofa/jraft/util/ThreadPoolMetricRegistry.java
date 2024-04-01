package com.alipay.sofa.jraft.util;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;


//该类用来监控线程执行任务的性能
public class ThreadPoolMetricRegistry {

    private static final MetricRegistry metricRegistry   = new MetricRegistry();
    private static final ThreadLocal<Timer.Context> timerThreadLocal = new ThreadLocal<>();


    public static MetricRegistry metricRegistry() {
        return metricRegistry;
    }

    public static ThreadLocal<Timer.Context> timerThreadLocal() {
        return timerThreadLocal;
    }
}
