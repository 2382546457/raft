package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.Lifecycle;
import com.alipay.sofa.jraft.rpc.impl.ConnectionClosedEventListener;

/**
 * @author : 小何
 * @Description : Raft 服务端要实现的接口
 * @date : 2024-03-31 14:33
 */
public interface RpcServer extends Lifecycle<Void> {
    /**
     * 给服务端注册连接关闭的事件监听器
     * @param listener
     */
    public void registerConnectionClosedEventListener(final ConnectionClosedEventListener listener);

    public void registerProcessor(final RpcProcessor<?> processor);

    public int boundPort();


}
