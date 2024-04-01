package com.alipay.sofa.jraft.error;

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
