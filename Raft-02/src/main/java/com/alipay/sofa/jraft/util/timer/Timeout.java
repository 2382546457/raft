package com.alipay.sofa.jraft.util.timer;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-01 14:54
 */
public interface Timeout {
    public Timer timer();

    public TimerTask task();

    public boolean isExpired();

    public boolean isCancelled();

    public boolean cancel();


}
