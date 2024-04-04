package com.alipay.sofa.jraft.rpc.closure;

import com.alipay.sofa.jraft.Closure;
import com.google.protobuf.Message;

/**
 * @author : 小何
 * @Description : 处理完响应要执行回调
 * @date : 2024-04-04 14:00
 */
public interface RpcResponseClosure<T extends Message> extends Closure {
    /**
     * 把收到的响应设置到 RpcResponseClosureAdapter适配器对象中
     * @param resp
     */
    public void setResponse(T resp);
}
