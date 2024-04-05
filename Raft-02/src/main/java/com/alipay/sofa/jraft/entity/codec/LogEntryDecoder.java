package com.alipay.sofa.jraft.entity.codec;

import com.alipay.sofa.jraft.entity.LogEntry;

/**
 * 日志解码器，将字节数组解码为日志条目
 */
public interface LogEntryDecoder {

    LogEntry decode(byte[] bs);
}