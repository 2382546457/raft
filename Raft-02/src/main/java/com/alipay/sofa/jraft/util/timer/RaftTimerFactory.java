package com.alipay.sofa.jraft.util.timer;

import com.alipay.sofa.jraft.core.Scheduler;

/**
 * @author : 小何
 * @Description : Raft 中定时任务器的工厂
 * @date : 2024-04-01 14:57
 */
public interface RaftTimerFactory {
    /**
     * 获取选举定时任务器
     * @param shared 是否为共享的
     * @param name 定时任务器的名字
     * @return Timer
     */
    Timer getElectionTimer(final boolean shared, final String name);

    public Timer getVoteTimer(final boolean shared, final String name);

    public Timer getStepDownTimer(final boolean shared, final String name);

    public Timer getSnapshotTimer(final boolean shared, final String name);

    public Scheduler getRaftScheduler(final boolean shared, final int workerNum, final String name);

    public Timer createTimer(final String name);

    Scheduler createScheduler(final int workerNum, final String name);
}
