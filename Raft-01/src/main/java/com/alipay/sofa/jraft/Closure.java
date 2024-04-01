package com.alipay.sofa.jraft;

/**
 * @author : 小何
 * @Description : 回调函数接口，NodeImpl 类的几个内部类都实现了它。那些类中就封装着要被回调的方法。
 * 当 Raft 内部的客户端发送的请求接口到响应后，这些方法就会被回调
 * @date : 2024-03-31 14:44
 */
public interface Closure {
    public void run(final Status status);
}
