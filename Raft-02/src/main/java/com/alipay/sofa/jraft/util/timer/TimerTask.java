package com.alipay.sofa.jraft.util.timer;

/**
 * @author : 小何
 * @Description : 定时任务
 * @date : 2024-04-01 14:54
 */
public interface TimerTask {
    public void run(final Timeout timeout) throws Exception;
}
