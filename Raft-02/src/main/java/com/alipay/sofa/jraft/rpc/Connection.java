package com.alipay.sofa.jraft.rpc;

/**
 * @author : 小何
 * @Description : 封装连接
 * @date : 2024-04-04 12:30
 */
public interface Connection {
    /**
     * 从连接中获取内容
     * @param key
     * @return
     */
    Object getAttribute(final String key);

    /**
     * 向连接中存放内容
     * @param key
     * @param value
     */
    void setAttribute(final String key, final Object value);

    /**
     * 向连接中存放内容，如果不存在就存放，存在就返回原有内容
     * @param key
     * @param value
     * @return
     */
    Object setAttributeIfAbsent(final String key, final Object value);

    /**
     * 关闭连接
     */
    void close();
}
