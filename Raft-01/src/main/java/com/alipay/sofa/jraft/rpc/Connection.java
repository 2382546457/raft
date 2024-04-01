package com.alipay.sofa.jraft.rpc;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-03-31 14:36
 */
public interface Connection {
    public Object getAttribute(final String key);
    public void setAttribute(final String key, final Object value);
    public Object setAttributeIfAbsent(final String key, final Object value);
    public void close();
}
