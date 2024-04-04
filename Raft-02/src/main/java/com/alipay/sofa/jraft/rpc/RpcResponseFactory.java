package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.RaftError;
import com.google.protobuf.Message;

/**
 * @author : 小何
 * @Description : 创建响应结果的工厂
 * @date : 2024-04-04 13:27
 */
public interface RpcResponseFactory {
    int ERROR_RESPONSE_NUM = 99;

    default Message newResponse(final Message parent, final Status status) {
        if (status != null) {
            return newResponse(parent, status.getCode(), status.getErrorMsg());
        }
        return newResponse(parent, 0 ,"OK");
    }
    default Message newResponse(final Message parent, final RaftError error, final String fmt, final Object...args) {
        return newResponse(parent, error.getNumber(), fmt, args);
    }
    default Message newResponse(final Message parent, final int code, final String fmt, final Object... args) {
        RpcRequests.ErrorResponse.Builder builder = RpcRequests.ErrorResponse.newBuilder();
        builder.setErrorCode(code);
        if (fmt != null) {
            builder.setErrorMsg(String.format(fmt, args));
        }
        return builder.build();
    }

}
