package com.alipay.sofa.jraft.rpc;


import com.alipay.sofa.jraft.Lifecycle;
import com.alipay.sofa.jraft.ReplicatorGroup;
import com.alipay.sofa.jraft.error.RemotingException;
import com.alipay.sofa.jraft.option.RpcOptions;
import com.alipay.sofa.jraft.util.Endpoint;

/**
 * @author : 小何
 * @Description : Rpc客户端
 * @date : 2024-04-04 14:17
 */
public interface RpcClient extends Lifecycle<RpcOptions> {
    boolean checkConnection(final Endpoint endpoint);

    boolean checkConnection(final Endpoint endpoint, final boolean createIfAbsent);

    void closeConnection(final Endpoint endpoint);

    void registerConnectionEventListener(final ReplicatorGroup replicatorGroup);

    default Object invokeSync(final Endpoint endpoint, final Object request, final long timeoutMs)
            throws InterruptedException, RemotingException {
        return invokeSync(endpoint, request, null, timeoutMs);
    }


    Object invokeSync(final Endpoint endpoint, final Object request, final InvokeContext ctx,
                      final long timeoutMs) throws InterruptedException, RemotingException;



    default void invokeAsync(final Endpoint endpoint, final Object request, final InvokeCallback callback,
                             final long timeoutMs) throws InterruptedException, RemotingException {
        invokeAsync(endpoint, request, null, callback, timeoutMs);
    }


    void invokeAsync(final Endpoint endpoint, final Object request, final InvokeContext ctx, final InvokeCallback callback,
                     final long timeoutMs) throws InterruptedException, RemotingException;
}
