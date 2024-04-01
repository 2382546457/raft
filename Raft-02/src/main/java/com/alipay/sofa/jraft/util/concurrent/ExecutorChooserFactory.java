package com.alipay.sofa.jraft.util.concurrent;


/**
 * 执行器选择器工厂的接口
 */
public interface ExecutorChooserFactory {

    /**
     * Returns a new {@link ExecutorChooser}.
     */
    ExecutorChooser newChooser(final SingleThreadExecutor[] executors);

    interface ExecutorChooser {

        /**
         * Returns the next {@link SingleThreadExecutor} to use.
         */
        SingleThreadExecutor next();

        /**
         * Returns the chosen {@link SingleThreadExecutor} to use.
         */
        SingleThreadExecutor select(final int index);
    }
}
