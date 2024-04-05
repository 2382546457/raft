package com.alipay.sofa.jraft.rpc.impl;

import com.alipay.sofa.jraft.rpc.Connection;

/**
 * @author : 小何
 * @Description : 连接关闭的监听器
 * @date : 2024-04-04 12:29
 */
public interface ConnectionClosedEventListener {
    public void onClosed(final String remoteAddress, final Connection connection);
}
