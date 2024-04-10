package com.alipay.sofa.jraft.storage;

import com.alipay.sofa.jraft.Lifecycle;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.option.LogStorageOptions;

/**
 * @author : 小何
 * @Description : 日志存储器需要实现的接口
 * @date : 2024-04-04 21:23
 */
public interface LogStorage extends Lifecycle<LogStorageOptions>, Storage {
    /**
     * 获取第一条日志索引
     * @return
     */
    public long getFirstLogIndex();

    /**
     * 获取最后一条日志索引
     * @return
     */
    public long getLastLogIndex();

    /**
     * 根据索引获取日志条目
     * @param index
     * @return
     */
    public LogEntry getEntry(final long index);

    @Deprecated
    long getTerm(final long index);

}
