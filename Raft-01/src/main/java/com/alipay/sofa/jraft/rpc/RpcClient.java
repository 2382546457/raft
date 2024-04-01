package com.alipay.sofa.jraft.rpc;

import com.alipay.remoting.exception.RemotingException;
import com.alipay.sofa.jraft.Lifecycle;
import com.alipay.sofa.jraft.ReplicatorGroup;
import com.alipay.sofa.jraft.error.InvokeTimeoutException;
import com.alipay.sofa.jraft.option.RpcOptions;
import com.alipay.sofa.jraft.util.Endpoint;

/**
 * @author : 小何
 * @Description : RPC 客户端
 * @date : 2024-03-31 17:30
 */
public interface RpcClient extends Lifecycle<RpcOptions> {
    /**
     * 检查连接
     * @param endpoint
     * @return
     */
    public boolean checkConnection(final Endpoint endpoint);

    /**
     * 检查连接，如果不存在就创建
     * @param endpoint
     * @param createIfAbsent
     * @return
     */
    public boolean checkConnection(final Endpoint endpoint, final boolean createIfAbsent);

    /**
     * TODO
     * @param replicatorGroup
     */
    public void registerConnectionEventListener(final ReplicatorGroup replicatorGroup);

    public void closeConnection(final Endpoint endpoint);

    /**
     * 异步发送
     * @param endpoint
     * @param request
     * @param ctx
     * @param callback
     * @param timeoutMs
     * @throws InterruptedException
     * @throws RemotingException
     */
    public void invokeAsync(final Endpoint endpoint,
                            final Object request,
                            final InvokeContext ctx,
                            final InvokeCallback callback,
                            final long timeoutMs) throws InterruptedException, RemotingException;

    /**
     * 同步发送
     * @param endpoint
     * @param request
     * @param ctx
     * @param timeoutMs
     * @return
     * @throws InterruptedException
     * @throws RemotingException
     */
    Object invokeSync(final Endpoint endpoint,
                      final Object request,
                      final InvokeContext ctx,
                      final long timeoutMs) throws InterruptedException, RemotingException, InvokeTimeoutException;

    public default void invokeAsync(final Endpoint endpoint, final Object request, final InvokeCallback callback,
                                    final long timeoutMs) throws InterruptedException, RemotingException {
        invokeAsync(endpoint, request, null, callback, timeoutMs);
    }
    public default Object invokeSync(final Endpoint endpoint,final Object request, final long timeoutMs) throws InterruptedException, RemotingException {
        return invokeSync(endpoint, request, null, timeoutMs);
    }


}
