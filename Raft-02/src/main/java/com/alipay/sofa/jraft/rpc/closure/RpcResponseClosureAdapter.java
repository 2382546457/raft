package com.alipay.sofa.jraft.rpc.closure;

import com.google.protobuf.Message;

public abstract class RpcResponseClosureAdapter<T extends Message> implements RpcResponseClosure<T> {

    private T resp;

    public T getResponse() {
        return this.resp;
    }

    @Override
    public void setResponse(T resp) {
        this.resp = resp;
    }
}
