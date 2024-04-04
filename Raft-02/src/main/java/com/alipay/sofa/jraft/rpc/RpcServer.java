package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.Lifecycle;
import com.alipay.sofa.jraft.rpc.impl.ConnectionClosedEventListener;

/**
 * @author : 小何
 * @Description : Rpc服务端
 * @date : 2024-04-04 13:34
 */
public interface RpcServer extends Lifecycle<Void> {
    /**
     * 给服务端注册连接关闭的事件监听器
     * @param listener
     */
    public void registerConnectionClosedEventListener(final ConnectionClosedEventListener listener);

    /**
     * 给服务端注册事件处理器
     * @param processor
     */
    public void registerProcessor(final RpcProcessor<?> processor);

    /**
     * 服务端绑定端口
     * @return
     */
    public int boundPort();
}
