package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.Lifecycle;
import com.alipay.sofa.jraft.option.RpcOptions;
import com.alipay.sofa.jraft.util.Endpoint;
import com.google.protobuf.Message;

import java.util.concurrent.Future;

/**
 * @author : 小何
 * @Description : 提供客户端的服务，RpcClient 中包含 ClientService
 * @date : 2024-03-31 17:38
 */
public interface ClientService extends Lifecycle<RpcOptions> {
    public boolean connect(final Endpoint endpoint);

    public boolean checkConnection(final Endpoint endpoint, final boolean createIfAbsent);
    public boolean disconnect(final Endpoint endpoint);

    public boolean isConnected(final Endpoint endpoint);

    <T extends Message> Future<Message> invokeWithDone(final Endpoint endpoint,
                                                       final Message request,
                                                       final RpcResponseClosure<T> done,
                                                       final int timeoutMs);
}
