package com.alipay.sofa.jraft.util;
import com.alipay.sofa.jraft.util.timer.HashedWheelTimer;
import com.alipay.sofa.jraft.util.timer.Timeout;
import com.alipay.sofa.jraft.util.timer.Timer;
import com.alipay.sofa.jraft.util.timer.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
public abstract class RepeatedTimer implements Describer {

    public static final Logger LOG = LoggerFactory.getLogger(RepeatedTimer.class);

    private final Lock lock = new ReentrantLock();
    //timer是HashedWheelTimer
    private final Timer timer;
    //实例是HashedWheelTimeout
    private Timeout timeout;
    private boolean stopped;
    private volatile boolean running;
    private volatile boolean destroyed;
    private volatile boolean invoking;
    //超时选举时间
    private volatile int timeoutMs;
    private final String name;

    public int getTimeoutMs() {
        return this.timeoutMs;
    }

    public RepeatedTimer(final String name, final int timeoutMs) {
        this(name, timeoutMs, new HashedWheelTimer(new NamedThreadFactory(name, true), 1, TimeUnit.MILLISECONDS, 2048));
    }


    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/11/23
     * @Description:该构造方法会在NodeImpl类的init方法中被调用
     */
    public RepeatedTimer(final String name, final int timeoutMs, final Timer timer) {
        super();
        this.name = name;
        this.timeoutMs = timeoutMs;
        this.stopped = true;
        this.timer = Requires.requireNonNull(timer, "timer");
    }


    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/11/23
     * @Description:该方法在NodeImpl类的init中实现了，就是调用handleElectionTimeout方法，开始进行预投票活动
     */
    protected abstract void onTrigger();

   /**
    * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
    * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
    * @Date:2023/11/23
    * @Description:该方法也在NodeImpl类的init中实现了，返回一个超时选举时间，这个时间是随机的
    */
    protected int adjustTimeout(final int timeoutMs) {
        return timeoutMs;
    }

    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/11/23
     * @Description:该类对象的核心方法，时间轮线程中执行的实际上就是该类对象中的run方法
     */
    public void run() {
        this.invoking = true;
        try {//调用下面这个方法后，会在该方法内部进一步调用handleElectionTimeout方法方法进行预投票活动
            onTrigger();
        } catch (final Throwable t) {
            LOG.error("Run timer failed.", t);
        }
        boolean invokeDestroyed = false;
        this.lock.lock();
        try {
            this.invoking = false;
            //判断该定时器有没有终止
            if (this.stopped) {
                this.running = false;
                invokeDestroyed = this.destroyed;
            } else {//走到这里说明定时器还在正常活动
                this.timeout = null;
                //再次调用schedule方法，其实就是向时间轮提交了下一个选举超时任务
                //到时间执行即可
                schedule();
            }
        } finally {
            this.lock.unlock();
        }
        if (invokeDestroyed) {
            onDestroy();
        }
    }


    public void runOnceNow() {
        this.lock.lock();
        try {
            if (this.timeout != null && this.timeout.cancel()) {
                this.timeout = null;
                run();
            }
        } finally {
            this.lock.unlock();
        }
    }


    protected void onDestroy() {
        LOG.info("Destroy timer: {}.", this);
    }

    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/11/23
     * @Description:启动定时器的方法
     */
    public void start() {
        this.lock.lock();
        try {
            if (this.destroyed) {
                return;
            }
            if (!this.stopped) {
                return;
            }
            this.stopped = false;
            if (this.running) {
                return;
            }
            this.running = true;
            //开始调度定时器管理的定时任务
            schedule();
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/11/23
     * @Description:重新启动该定时器
     */
    public void restart() {
        this.lock.lock();
        try {
            if (this.destroyed) {
                return;
            }
            this.stopped = false;
            this.running = true;
            schedule();
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/11/23
     * @Description:启动该类对象代表的定时器之后，就会在下面这个方法中创建一个TimerTask任务，并且把这个任务提交到
     * 时间轮中，设置好多少时间之后执行该定时任务
     */
    private void schedule() {
        if (this.timeout != null) {
            this.timeout.cancel();
        }
        final TimerTask timerTask = timeout -> {
            try {
                RepeatedTimer.this.run();
            } catch (final Throwable t) {
                LOG.error("Run timer task failed, taskName={}.", RepeatedTimer.this.name, t);
            }
        };
        this.timeout = this.timer.newTimeout(timerTask, adjustTimeout(this.timeoutMs), TimeUnit.MILLISECONDS);
    }


    //重置超时选举时间，然后调度超时选举任务
    public void reset(final int timeoutMs) {
        this.lock.lock();
        this.timeoutMs = timeoutMs;
        try {
            if (this.stopped) {
                return;
            }
            if (this.running) {
                schedule();
            }
        } finally {
            this.lock.unlock();
        }
    }


    public void reset() {
        this.lock.lock();
        try {
            reset(this.timeoutMs);
        } finally {
            this.lock.unlock();
        }
    }


    public void destroy() {
        boolean invokeDestroyed = false;
        this.lock.lock();
        try {
            if (this.destroyed) {
                return;
            }
            this.destroyed = true;
            if (!this.running) {
                invokeDestroyed = true;
            }
            if (this.stopped) {
                return;
            }
            this.stopped = true;
            if (this.timeout != null) {
                if (this.timeout.cancel()) {
                    invokeDestroyed = true;
                    this.running = false;
                }
                this.timeout = null;
            }
        } finally {
            this.lock.unlock();
            this.timer.stop();
            if (invokeDestroyed) {
                onDestroy();
            }
        }
    }


    public void stop() {
        this.lock.lock();
        try {
            if (this.stopped) {
                return;
            }
            this.stopped = true;
            if (this.timeout != null) {
                this.timeout.cancel();
                this.running = false;
                this.timeout = null;
            }
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void describe(final Printer out) {
        final String _describeString;
        this.lock.lock();
        try {
            _describeString = toString();
        } finally {
            this.lock.unlock();
        }
        out.print("  ") //
                .println(_describeString);
    }

    @Override
    public String toString() {
        return "RepeatedTimer{" + "timeout=" + this.timeout + ", stopped=" + this.stopped + ", running=" + this.running
                + ", destroyed=" + this.destroyed + ", invoking=" + this.invoking + ", timeoutMs=" + this.timeoutMs
                + ", name='" + this.name + '\'' + '}';
    }
}