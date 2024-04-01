package com.alipay.sofa.jraft.rpc;

import java.util.concurrent.Executor;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-03-31 14:54
 */
public interface InvokeCallback {
    public void complete(final Object result, final Throwable error);

    default Executor executor() {
        return null;
    }
}
