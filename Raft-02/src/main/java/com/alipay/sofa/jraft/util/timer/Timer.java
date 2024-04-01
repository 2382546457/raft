package com.alipay.sofa.jraft.util.timer;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-01 14:53
 */
public interface Timer {
    /**
     * 新建 Timeout，一个 Timeout 管理一个 TimerTask
     * @param task
     * @param delay
     * @param unit
     * @return
     */
    Timeout newTimeout(final TimerTask task, final long delay, final TimeUnit unit);

    Set<Timeout> stop();
}
