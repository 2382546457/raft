package com.alipay.sofa.jraft.entity.v2;

import com.alipay.sofa.jraft.entity.AutoDetectDecoder;
import com.alipay.sofa.jraft.entity.codec.LogEntryCodecFactory;
import com.alipay.sofa.jraft.entity.codec.LogEntryDecoder;
import com.alipay.sofa.jraft.entity.codec.LogEntryEncoder;

public class LogEntryV2CodecFactory implements LogEntryCodecFactory {

    private static final LogEntryV2CodecFactory INSTANCE = new LogEntryV2CodecFactory();

    public static LogEntryV2CodecFactory getInstance() {
        return INSTANCE;
    }

    // BB-8 and R2D2 are good friends.
    //魔数
    public static final byte[] MAGIC_BYTES = new byte[] { (byte) 0xBB, (byte) 0xD2 };
    // Codec version
    //版本号
    public static final byte   VERSION     = 1;
    //预留字段
    public static final byte[] RESERVED    = new byte[3];
    //上面三个属性加起来的长度
    public static final int    HEADER_SIZE = MAGIC_BYTES.length + 1 + RESERVED.length;

    //下面两个方法提供了编码器和解码器
    @Override
    public LogEntryEncoder encoder() {
        return V2Encoder.INSTANCE;
    }

    @Override
    public LogEntryDecoder decoder() {
        return AutoDetectDecoder.INSTANCE;
    }

    private LogEntryV2CodecFactory() {
    }
}