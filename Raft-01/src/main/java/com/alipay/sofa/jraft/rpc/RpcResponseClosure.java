package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.Closure;
import com.google.protobuf.Message;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-03-31 17:57
 */
public interface RpcResponseClosure<T extends Message> extends Closure {
    /**
     * 把接收到的响应设置到 RpcResponseClosureAdapter适配器对象中
     * @param response
     */
    public void setResponse(T response);
}
