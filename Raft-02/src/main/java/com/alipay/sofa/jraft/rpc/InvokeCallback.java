package com.alipay.sofa.jraft.rpc;

import java.util.concurrent.Executor;

/**
 * @author : 小何
 * @Description : 异步发送需要的回调接口
 * @date : 2024-04-04 14:21
 */
public interface InvokeCallback {
    void complete(final Object result, final Throwable err);
    default Executor executor() {
        return null;
    }
}
