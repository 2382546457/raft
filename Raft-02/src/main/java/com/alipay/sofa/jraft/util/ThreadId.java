package com.alipay.sofa.jraft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;

/**
 * @author : 小何
 * @Description : 持有复制器的引用，同时提供了同步锁
 * @date : 2024-04-05 12:03
 */
public class ThreadId {
    public interface OnError {
        void onError(final ThreadId id, final Object data, final int errorCode);
    }

    private static final Logger logger = LoggerFactory.getLogger(ThreadId.class);

    private final Object data;
    private final ReentrantLock lock = new ReentrantLock();
    private final OnError onError;
    private volatile boolean destroyed;

    public ThreadId(final Object data, final OnError onError) {
        super();
        this.data = data;
        this.onError = onError;
        this.destroyed = false;
    }

    public Object getData() {
        return this.data;
    }
    public Object lock() {
        if (this.destroyed) {
            return null;
        }
        this.lock.lock();
        if (this.destroyed) {
            this.lock.unlock();
            return null;
        }
        return this.data;
    }
    public void unlock() {
        if (!this.lock.isHeldByCurrentThread()) {
            logger.warn("Fail to unlock with {}, the lock is not held by current thread {}.", this.data,
                    Thread.currentThread());
            return;
        }
        this.lock.unlock();
    }
    public void join() {
        while (!this.destroyed) {
            ThreadHelper.onSpinWait();
        }
    }

    @Override
    public String toString() {
        return this.data.toString();
    }

    public void unlockAndDestroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        unlock();
    }
    public void setError(final int errorCode) {
        if (this.destroyed) {
            logger.warn("ThreadId: {} already destroyed, ignore error code: {}", this.data, errorCode);
            return;
        }
        this.lock.lock();
        try {
            if (this.destroyed) {
                logger.warn("ThreadId: {} already destroyed, ignore error code: {}", this.data, errorCode);
                return;
            }
            if (this.onError != null) {
                this.onError.onError(this, this.data, errorCode);
            }
        } finally {
            if (!this.destroyed) {
                this.lock.unlock();
            }
        }
    }
}
