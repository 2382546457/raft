package com.alipay.sofa.jraft.rpc;

/**
 * @author : 小何
 * @Description : Rpc上下文
 * @date : 2024-03-31 14:37
 */
public interface RpcContext {
    /**
     * 发送响应
     * @param responseObj
     */
    public void sendResponse(final Object responseObj);

    /**
     * 获取当前连接
     * @return
     */
    public Connection getConnection();

    /**
     * 获取连接地址
     * @return
     */
    public String getRemoteAddress();
}
