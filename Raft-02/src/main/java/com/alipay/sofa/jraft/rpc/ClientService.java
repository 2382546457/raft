package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.Lifecycle;
import com.alipay.sofa.jraft.option.RpcOptions;
import com.alipay.sofa.jraft.rpc.closure.RpcResponseClosure;
import com.alipay.sofa.jraft.util.Endpoint;
import com.google.protobuf.Message;

import java.util.concurrent.Future;

/**
 * 通用的客户端服务，比如建立连接、检查连接、断开连接...
 */
public interface ClientService extends Lifecycle<RpcOptions> {

    boolean connect(final Endpoint endpoint);

    boolean checkConnection(final Endpoint endpoint, final boolean createIfAbsent);

    boolean disconnect(final Endpoint endpoint);


    boolean isConnected(final Endpoint endpoint);


    <T extends Message> Future<Message> invokeWithDone(final Endpoint endpoint, final Message request,
                                                       final RpcResponseClosure<T> done, final int timeoutMs);
}
