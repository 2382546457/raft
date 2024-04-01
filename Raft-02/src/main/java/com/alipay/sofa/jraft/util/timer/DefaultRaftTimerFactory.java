package com.alipay.sofa.jraft.util.timer;

import com.alipay.sofa.jraft.core.Scheduler;
import com.alipay.sofa.jraft.core.TimerManager;
import com.alipay.sofa.jraft.util.NamedThreadFactory;
import com.alipay.sofa.jraft.util.SPI;
import com.alipay.sofa.jraft.util.SystemPropertyUtil;
import com.alipay.sofa.jraft.util.Utils;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-01 15:16
 */
@SPI
public class DefaultRaftTimerFactory implements RaftTimerFactory {
    private static final String GLOBAL_ELECTION_TIMER_WORKERS = "jraft.timer.global_election_timer_workers";
    private static final String GLOBAL_VOTE_TIMER_WORKERS = "jraft.timer.global_vote_timer_workers";
    private static final String GLOBAL_STEP_DOWN_TIMER_WORKERS = "jraft.timer.global_step_down_timer_workers";
    private static final String GLOBAL_SNAPSHOT_TIMER_WORKERS = "jraft.timer.global_snapshot_timer_workers";
    private static final String GLOBAL_SCHEDULER_WORKERS = "jraft.timer.global_scheduler_workers";

    private static final TimerSharedRef ELECTION_TIMER_REF = new TimerSharedRef(
            SystemPropertyUtil.getInt(
                    GLOBAL_ELECTION_TIMER_WORKERS,
                    Utils.cpus()),
            "JRaft-Global-ElectionTimer");

    private static final TimerSharedRef VOTE_TIMER_REF = new TimerSharedRef(
            SystemPropertyUtil.getInt(
                    GLOBAL_VOTE_TIMER_WORKERS,
                    Utils.cpus()),
            "JRaft-Global-VoteTimer");

    private static final TimerSharedRef STEP_DOWN_TIMER_REF = new TimerSharedRef(
            SystemPropertyUtil.getInt(
                    GLOBAL_STEP_DOWN_TIMER_WORKERS,
                    Utils.cpus()),
            "JRaft-Global-StepDownTimer");

    private static final TimerSharedRef SNAPSHOT_TIMER_REF = new TimerSharedRef(
            SystemPropertyUtil.getInt(
                    GLOBAL_SNAPSHOT_TIMER_WORKERS,
                    Utils.cpus()),
            "JRaft-Global-SnapshotTimer");

    private static final SchedulerSharedRef SCHEDULER_REF = new SchedulerSharedRef(
            SystemPropertyUtil.getInt(
                    GLOBAL_SCHEDULER_WORKERS,
                    Utils.cpus() * 3 > 20 ? 20 : Utils
                            .cpus() * 3),
            "JRaft-Node-ScheduleThreadPool");

    @Override
    public Timer getElectionTimer(final boolean shared, final String name) {
        return shared ? ELECTION_TIMER_REF.getRef() : createTimer(name);
    }

    @Override
    public Timer getVoteTimer(final boolean shared, final String name) {
        return shared ? VOTE_TIMER_REF.getRef() : createTimer(name);
    }

    @Override
    public Timer getStepDownTimer(final boolean shared, final String name) {
        return shared ? STEP_DOWN_TIMER_REF.getRef() : createTimer(name);
    }

    @Override
    public Timer getSnapshotTimer(final boolean shared, final String name) {
        return shared ? SNAPSHOT_TIMER_REF.getRef() : createTimer(name);
    }

    @Override
    public Scheduler getRaftScheduler(final boolean shared, final int workerNum, final String name) {
        return shared ? SCHEDULER_REF.getRef() : createScheduler(workerNum, name);
    }

    @Override
    public Timer createTimer(final String name) {
        return new HashedWheelTimer(new NamedThreadFactory(name, true), 1, TimeUnit.MILLISECONDS, 2048);
    }

    @Override
    public Scheduler createScheduler(final int workerNum, final String name) {
        return new TimerManager(workerNum, name);
    }



    private static abstract class Shared<T> {
        /**
         * 这个Timer被有多少个引用
         */
        private AtomicInteger refCount = new AtomicInteger(0);
        /**
         * 这个Timer是否已经启动
         */
        private AtomicBoolean started = new AtomicBoolean(true);

        protected final T shared;

        protected Shared(T shared) {
            this.shared = shared;
        }

        /**
         * 获得这个引用，也就是 shared
         */
        public abstract T current();

        public T getRef() {
            if (this.started.get()) {
                this.refCount.incrementAndGet();
                return current();
            }
            throw new IllegalStateException("Shared shutdown");
        }

        public boolean isShutdown() {
            return !this.started.get();
        }

        public boolean mayShutdown() {
            return this.refCount.decrementAndGet() <= 0 && this.started.compareAndSet(true, false);
        }
    }

    /**
     * 共享 Timer
     */
    private static class SharedTimer extends Shared<Timer> implements Timer {
        protected SharedTimer(Timer shared) {
            super(shared);
        }

        @Override
        public Timer current() {
            return this;
        }

        @Override
        public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
            return this.shared.newTimeout(task, delay, unit);
        }

        /**
         * 停止Timer，返回所有未执行任务
         */
        @Override
        public Set<Timeout> stop() {
            if (mayShutdown()) {
                return this.shared.stop();
            }
            return Collections.emptySet();
        }
    }

    /**
     * 共享 Scheduler
     */
    private static class SharedScheduler extends Shared<Scheduler> implements Scheduler {

        protected SharedScheduler(Scheduler shared) {
            super(shared);
        }

        @Override
        public Scheduler current() {
            return this;
        }

        @Override
        public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
            return this.shared.schedule(command, delay, unit);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay,
                                                      final long period, final TimeUnit unit) {
            return this.shared.scheduleAtFixedRate(command, initialDelay, period, unit);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, final long initialDelay,
                                                         final long delay, final TimeUnit unit) {
            return this.shared.scheduleWithFixedDelay(command, initialDelay, delay, unit);
        }

        @Override
        public void shutdown() {
            if (mayShutdown()) {
                this.shared.shutdown();
            }
        }
    }


    private static abstract class SharedRef<T> {
        private final int workerNum;
        private final String name;
        private Shared<T> shared;

        public SharedRef(int workerNum, String name) {
            this.workerNum = workerNum;
            this.name = name;
        }

        public synchronized T getRef() {
            if (this.shared == null || this.shared.isShutdown()) {
                this.shared = create(this.workerNum, this.name);
            }
            return this.shared.getRef();
        }
        public abstract Shared<T> create(final int workerNum, final String name);
    }

    private static class TimerSharedRef extends SharedRef<Timer> {
        public TimerSharedRef(int workerNum, String name) {
            super(workerNum, name);
        }

        @Override
        public Shared<Timer> create(int workerNum, String name) {
            return new SharedTimer(new DefaultTimer(workerNum, name));
        }
    }

    private static class SchedulerSharedRef extends SharedRef<Scheduler> {

        public SchedulerSharedRef(int workerNum, String name) {
            super(workerNum, name);
        }

        @Override
        public Shared<Scheduler> create(final int workerNum, final String name) {
            return new SharedScheduler(new TimerManager(workerNum, name));
        }
    }
}
