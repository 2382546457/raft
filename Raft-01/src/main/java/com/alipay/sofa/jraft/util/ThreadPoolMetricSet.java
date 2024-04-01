package com.alipay.sofa.jraft.util;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;



public final class ThreadPoolMetricSet implements MetricSet {

    private final ThreadPoolExecutor executor;

    public ThreadPoolMetricSet(ThreadPoolExecutor rpcExecutor) {
        super();
        this.executor = rpcExecutor;
    }


    @Override
    public Map<String, Metric> getMetrics() {
        final Map<String, Metric> gauges = new HashMap<>();
        gauges.put("pool-size", (Gauge<Integer>) executor::getPoolSize);
        gauges.put("queued", (Gauge<Integer>) executor.getQueue()::size);
        gauges.put("active", (Gauge<Integer>) executor::getActiveCount);
        gauges.put("completed", (Gauge<Long>) executor::getCompletedTaskCount);
        return gauges;
    }
}