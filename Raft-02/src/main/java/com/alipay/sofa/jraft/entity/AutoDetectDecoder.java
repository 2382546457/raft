package com.alipay.sofa.jraft.entity;

import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.codec.LogEntryDecoder;
import com.alipay.sofa.jraft.entity.v2.V2Decoder;


public class AutoDetectDecoder implements LogEntryDecoder {

    private AutoDetectDecoder() {

    }

    public static final AutoDetectDecoder INSTANCE = new AutoDetectDecoder();

    @Override
    public LogEntry decode(final byte[] bs) {
        if (bs == null || bs.length < 1) {
            return null;
        }
        return V2Decoder.INSTANCE.decode(bs);
    }

}