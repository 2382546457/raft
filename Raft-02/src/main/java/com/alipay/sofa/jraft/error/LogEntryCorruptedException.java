package com.alipay.sofa.jraft.error;

public class LogEntryCorruptedException extends JRaftException {
    private static final long serialVersionUID = 5664520219607766929L;

    public LogEntryCorruptedException() {
        super();

    }

    public LogEntryCorruptedException(final String message, final Throwable cause, final boolean enableSuppression,
                                      final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);

    }

    public LogEntryCorruptedException(final String message, final Throwable cause) {
        super(message, cause);

    }

    public LogEntryCorruptedException(final String message) {
        super(message);

    }

    public LogEntryCorruptedException(final Throwable cause) {
        super(cause);

    }

}