package com.alipay.sofa.jraft.util;

import com.alipay.sofa.jraft.entity.codec.LogEntryCodecFactory;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.storage.LogStorage;
import com.alipay.sofa.jraft.storage.RaftMetaStorage;

/**
 * @author : 小何
 * @Description : 创建服务的工厂
 * @date : 2024-04-04 23:55
 */
public interface JRaftServiceFactory {
    /**
     * 创建日志存储器
     * @param uri
     * @param raftOptions
     * @return
     */
    LogStorage createLogStorage(String uri, RaftOptions raftOptions);

    /**
     * 创建元数据存储器
     * @param uri
     * @param raftOptions
     * @return
     */
    RaftMetaStorage createRaftMetaStorage(String uri, RaftOptions raftOptions);

    /**
     * 创建编码解码工厂，内含编码器与解码器
     * @return
     */
    LogEntryCodecFactory createLogEntryCodecFactory();
}
