package com.alipay.sofa.jraft.entity.codec;

import com.alipay.sofa.jraft.entity.LogEntry;

/**
 * 日志编码器，将日志条目编码为字节数组
 */
public interface LogEntryEncoder {


    byte[] encode(LogEntry log);
}