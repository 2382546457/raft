package com.alipay.sofa.jraft.rpc;

/**
 * @author : 小何
 * @Description : 一次请求的上下文
 * @date : 2024-04-04 13:38
 */
public interface RpcContext {
    /**
     * 给这个请求发送响应
     * @param responseObj
     */
    public void sendResponse(final Object responseObj);

    /**
     * 上下文中拥有对应请求端的连接
     * @return
     */
    public Connection getConnection();

    /**
     * 对方的IP+PORT
     * @return
     */
    public String getRemoteAddress();
}
