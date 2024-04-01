package com.alipay.sofa.jraft.rpc.impl;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author : 小何
 * @Description : 重新实现异步任务
 * @date : 2024-04-01 10:56
 */
public class FutureImpl<R> implements Future<R> {
    protected final ReentrantLock lock;
    protected boolean isDone;
    protected CountDownLatch latch;
    protected boolean isCancelled;
    protected Throwable failure;
    protected R result;

    public FutureImpl() {
        this.lock = new ReentrantLock();
    }

    public FutureImpl(ReentrantLock lock) {
        this.lock = lock;
        this.latch = new CountDownLatch(1);
    }

    /**
     * 得到当前结果
     * @return
     */
    public R getResult() {
        this.lock.lock();
        try {
            return this.result;
        } finally {
            this.lock.unlock();
        }
    }
    public Throwable getFailure() {
        this.lock.lock();
        try {
            return this.failure;
        } finally {
            this.lock.unlock();
        }
    }
    public void setResult(R result) {
        this.lock.lock();
        try {
            this.result = result;
            notifyHaveResult();
        } finally {
            this.lock.unlock();
        }
    }
    public void failure(final Throwable failure) {
        this.lock.lock();
        try {
            this.failure = failure;
            notifyHaveResult();
        } finally {
            this.lock.unlock();
        }
    }
    protected void notifyHaveResult() {
        this.isDone = true;
        this.latch.countDown();
    }
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        this.lock.lock();
        try {
            this.isCancelled = true;
            notifyHaveResult();
            return true;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public boolean isCancelled() {
        try {
            this.lock.lock();
            return this.isCancelled;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public boolean isDone() {
        this.lock.lock();
        try {
            return this.isDone;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public R get() throws InterruptedException, ExecutionException {
        // 阻塞当前线程
        this.latch.await();
        this.lock.lock();
        try {
            if (this.isCancelled) {
                throw new CancellationException();
            } else if (this.failure != null) {
                throw new ExecutionException(this.failure);
            }
            return this.result;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        final boolean isTimeout = !latch.await(timeout, unit);
        this.lock.lock();
        try {
            if (!isTimeout) {
                if (this.isCancelled) {
                    throw new CancellationException();
                } else if (this.failure != null) {
                    throw new ExecutionException(this.failure);
                }
                return this.result;
            } else {
                throw new TimeoutException();
            }
        } finally {
            this.lock.unlock();
        }
    }
}
