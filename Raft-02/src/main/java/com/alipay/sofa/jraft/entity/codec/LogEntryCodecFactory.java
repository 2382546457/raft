package com.alipay.sofa.jraft.entity.codec;

/**
 * 日志条目编码、解码工厂，可以创建编码、解码器
 */
public interface LogEntryCodecFactory {

    LogEntryEncoder encoder();


    LogEntryDecoder decoder();
}