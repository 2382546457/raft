package com.alipay.sofa.jraft.core;

import com.alipay.sofa.jraft.entity.codec.LogEntryCodecFactory;
import com.alipay.sofa.jraft.entity.v2.LogEntryV2CodecFactory;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.storage.LogStorage;
import com.alipay.sofa.jraft.storage.RaftMetaStorage;
import com.alipay.sofa.jraft.storage.impl.LocalRaftMetaStorage;
import com.alipay.sofa.jraft.storage.impl.RocksDBLogStorage;
import com.alipay.sofa.jraft.util.JRaftServiceFactory;
import com.alipay.sofa.jraft.util.Requires;
import com.alipay.sofa.jraft.util.SPI;
import org.apache.commons.lang.StringUtils;

/**
 * @author : 小何
 * @Description : 默认创建服务的工厂，可以创建 日志存储器、源数据存储器、编码解码工厂
 * @date : 2024-04-04 23:58
 */
@SPI
public class DefaultJRaftServiceFactory implements JRaftServiceFactory {
    public static DefaultJRaftServiceFactory newInstance() {
        return new DefaultJRaftServiceFactory();
    }

    @Override
    public LogStorage createLogStorage(String uri, RaftOptions raftOptions) {
        Requires.requireTrue(StringUtils.isNotBlank(uri), "Blank log storage uri.");
        return new RocksDBLogStorage(uri, raftOptions);
    }

    @Override
    public RaftMetaStorage createRaftMetaStorage(String uri, RaftOptions raftOptions) {
        Requires.requireTrue(!StringUtils.isBlank(uri), "Blank raft meta storage uri.");
        //在这里创建元数据存储器
        return new LocalRaftMetaStorage(uri, raftOptions);
    }

    @Override
    public LogEntryCodecFactory createLogEntryCodecFactory() {
        return LogEntryV2CodecFactory.getInstance();
    }
}
