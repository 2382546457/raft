package com.alipay.sofa.jraft.util.concurrent;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * jraft框架自己定义的提供读写锁功能的类，其内部还是使用了jdk的ReentrantReadWriteLock
 */
public abstract class LongHeldDetectingReadWriteLock implements ReadWriteLock {
    //枚举类，表示创建的是读锁还是写锁
    public enum AcquireMode {
        Read, Write
    }
    //读锁
    private final Lock rLock;
    //写锁
    private final Lock wLock;


    //构造方法，在该方法中传入了一个maxBlockingTimeToReport时间，这意味着如果在这个时间范围内获取锁没有成功
    //就记录日志，报告给用户
    public LongHeldDetectingReadWriteLock(long maxBlockingTimeToReport, TimeUnit unit) {
        this(false, maxBlockingTimeToReport, unit);
    }

    //真正创建读写锁的构造方法，这个构造方法的第一个参数fair，表示是否公平，默认为不公平的
    //非公平这种概念不用我解释了吧。。
    public LongHeldDetectingReadWriteLock(boolean fair, long maxBlockingTimeToReport, TimeUnit unit) {
        //创建一个读写锁对象，这个RwLock就继承了jdk中的ReentrantReadWriteLock类
        final RwLock rwLock = new RwLock(fair);
        final long maxBlockingNanos = unit.toNanos(maxBlockingTimeToReport);
        if (maxBlockingNanos > 0) {
            //创建读锁
            this.rLock = new LongHeldDetectingLock(AcquireMode.Read, rwLock, maxBlockingNanos);
            //创建写锁，注意，这两个锁都是带超时时间的，注意，这个超时时间并不是说在这个时间范围内没有得到锁
            //就报错，而是在这个范围内没获取到锁就记录一条告警日志
            this.wLock = new LongHeldDetectingLock(AcquireMode.Write, rwLock, maxBlockingNanos);
        } else {//下面创建的这两个锁没有超时时间
            this.rLock = rwLock.readLock();
            this.wLock = rwLock.writeLock();
        }
    }

    @Override
    public Lock readLock() {
        return this.rLock;
    }

    @Override
    public Lock writeLock() {
        return this.wLock;
    }

    //该方法在NodeImpl的内部类NodeReadWriteLock中被实现了
    public abstract void report(final AcquireMode acquireMode, final Thread heldThread,
                                final Collection<Thread> queuedThreads, final long blockedNanos);

    //提供读写锁功能的内部类
    static class RwLock extends ReentrantReadWriteLock {

        private static final long serialVersionUID = -1783358548846940445L;

        public RwLock() {
        }

        public RwLock(boolean fair) {
            super(fair);
        }

        @Override
        public Thread getOwner() {
            return super.getOwner();
        }

        @Override
        public Collection<Thread> getQueuedThreads() {
            return super.getQueuedThreads();
        }
    }

    class LongHeldDetectingLock implements Lock {

        private final AcquireMode mode;
        private final RwLock      parent;
        private final Lock        delegate;
        private final long        maxBlockingNanos;

        LongHeldDetectingLock(AcquireMode mode, RwLock parent, long maxBlockingNanos) {
            this.mode = mode;
            this.parent = parent;
            //在这里从ReentrantReadWriteLock中获取到了读锁/写锁，就是根据这个枚举对象来判断的
            this.delegate = mode == AcquireMode.Read ? parent.readLock() : parent.writeLock();
            this.maxBlockingNanos = maxBlockingNanos;
        }

        //获取锁的方法
        @Override
        public void lock() {
            final long start = System.nanoTime();
            //得到当前正在获取锁的线程
            final Thread owner = this.parent.getOwner();
            try {//获取锁
                this.delegate.lock();
            } finally {//最后判断一下当前线程获取锁的时间是否超出预期了
                final long elapsed = System.nanoTime() - start;
                if (elapsed > this.maxBlockingNanos) {
                    //超出了就记录一条告警日志
                    report(this.mode, owner, this.parent.getQueuedThreads(), elapsed);
                }
            }
        }

        //下面这几个都是常规方法，就不再添加注释了
        @Override
        public void lockInterruptibly() throws InterruptedException {
            final long start = System.nanoTime();
            final Thread owner = this.parent.getOwner();
            try {
                this.delegate.lockInterruptibly();
            } finally {
                final long elapsed = System.nanoTime() - start;
                if (elapsed > this.maxBlockingNanos) {
                    report(this.mode, owner, this.parent.getQueuedThreads(), elapsed);
                }
            }
        }

        @Override
        public boolean tryLock() {
            return this.delegate.tryLock();
        }

        @Override
        public boolean tryLock(final long time, final TimeUnit unit) throws InterruptedException {
            return this.delegate.tryLock(time, unit);
        }

        @Override
        public void unlock() {
            this.delegate.unlock();
        }

        @Override
        public Condition newCondition() {
            return this.delegate.newCondition();
        }
    }
}
