package com.alipay.sofa.jraft.error;

/**
 * 对应的消息类型没有找到，意思就是服务端接收到了消息，但是没法处理
 */
public class MessageClassNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 4684584394785943114L;

    public MessageClassNotFoundException() {
        super();
    }

    public MessageClassNotFoundException(String message, Throwable cause, boolean enableSuppression,
                                         boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public MessageClassNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageClassNotFoundException(String message) {
        super(message);
    }

    public MessageClassNotFoundException(Throwable cause) {
        super(cause);
    }
}
