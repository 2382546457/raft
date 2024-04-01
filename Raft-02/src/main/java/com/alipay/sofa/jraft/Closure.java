package com.alipay.sofa.jraft;

/**
 * @author : 小何
 * @Description : 回调函数接口
 * @date : 2024-04-01 14:17
 */
public interface Closure {
    public void run(final Status status);
}
