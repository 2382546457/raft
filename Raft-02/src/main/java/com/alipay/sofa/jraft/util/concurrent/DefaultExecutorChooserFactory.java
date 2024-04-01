package com.alipay.sofa.jraft.util.concurrent;

import com.alipay.sofa.jraft.util.Ints;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * 执行器选择器，这个选择器在Netty中也出现过，从执行器组中返回一个执行器
 */
public final class DefaultExecutorChooserFactory implements ExecutorChooserFactory {

    public static final DefaultExecutorChooserFactory INSTANCE = new DefaultExecutorChooserFactory();

    @Override
    public ExecutorChooser newChooser(final SingleThreadExecutor[] executors) {
        if (Ints.isPowerOfTwo(executors.length)) {
            return new PowerOfTwoExecutorChooser(executors);
        } else {
            return new GenericExecutorChooser(executors);
        }
    }

    private DefaultExecutorChooserFactory() {
    }

    private static class PowerOfTwoExecutorChooser extends AbstractExecutorChooser {

        PowerOfTwoExecutorChooser(SingleThreadExecutor[] executors) {
            super(executors);
        }

        @Override
        public SingleThreadExecutor select(final int index) {
            return this.executors[index & this.executors.length - 1];
        }
    }

    private static class GenericExecutorChooser extends AbstractExecutorChooser {

        protected GenericExecutorChooser(SingleThreadExecutor[] executors) {
            super(executors);
        }

        @Override
        public SingleThreadExecutor select(final int index) {
            return this.executors[Math.abs(index % this.executors.length)];
        }
    }

    private static abstract class AbstractExecutorChooser implements ExecutorChooser {

        protected final AtomicInteger idx = new AtomicInteger();
        protected final SingleThreadExecutor[] executors;

        protected AbstractExecutorChooser(SingleThreadExecutor[] executors) {
            this.executors = executors;
        }

        @Override
        public SingleThreadExecutor next() {
            return select(this.idx.getAndIncrement());
        }
    }
}
