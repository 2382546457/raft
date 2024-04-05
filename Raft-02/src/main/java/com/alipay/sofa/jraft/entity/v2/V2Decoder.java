package com.alipay.sofa.jraft.entity.v2;

import com.alipay.sofa.jraft.JRaftUtils;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.codec.LogEntryDecoder;
import com.alipay.sofa.jraft.util.AsciiStringUtil;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ZeroByteStringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;



/**
 * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/11/30
 * @Description:日志的解码器对象
 */
public class V2Decoder implements LogEntryDecoder {

    private static final Logger LOG      = LoggerFactory.getLogger(V2Decoder.class);

    public static final V2Decoder INSTANCE = new V2Decoder();

    //在这个方法内部，会把方法参这一堆字节解析成一个PBLogEntry对象
    //将日志从数据库中读取出来时，会调用这个解码器对日志进行解码，把日志数据解析为一个LogEntry对象
    @Override
    public LogEntry decode(final byte[] bs) {
        if (bs == null || bs.length < LogEntryV2CodecFactory.HEADER_SIZE) {
            return null;
        }

        int i = 0;
        for (; i < LogEntryV2CodecFactory.MAGIC_BYTES.length; i++) {
            if (bs[i] != LogEntryV2CodecFactory.MAGIC_BYTES[i]) {
                return null;
            }
        }

        if (bs[i++] != LogEntryV2CodecFactory.VERSION) {
            return null;
        }
        // Ignored reserved
        i += LogEntryV2CodecFactory.RESERVED.length;
        try {//就在这里，把字节解析成PBLogEntry对象，字节就是从数据库中获得的
            final LogOutter.PBLogEntry entry = LogOutter.PBLogEntry.parseFrom(ZeroByteStringHelper.wrap(bs, i, bs.length - i));
            //创建一个LogEntry对象，用来封装解析好的数据
            final LogEntry log = new LogEntry();
            //设置条目类型，看看是配置信息日志还是业务日志
            log.setType(entry.getType());
            //设置日志索引
            log.getId().setIndex(entry.getIndex());
            //设置任期
            log.getId().setTerm(entry.getTerm());
            //设置校验和
            if (entry.hasChecksum()) {
                log.setChecksum(entry.getChecksum());
            }//下面的我就不再重复了，大家根据LogEntry内部结构，就知道解析的是个成员变量了
            //在第二版本，LogEntry内部的成员变量还无法解析完整，下面的这些都没有数据，等到更新到配置变更的时候
            //下面这些才能被完整解析
            if (entry.getPeersCount() > 0) {
                final List<PeerId> peers = new ArrayList<>(entry.getPeersCount());
                for (final ByteString bstring : entry.getPeersList()) {
                    peers.add(JRaftUtils.getPeerId(AsciiStringUtil.unsafeDecode(bstring)));
                }
                log.setPeers(peers);
            }
            if (entry.getOldPeersCount() > 0) {
                final List<PeerId> peers = new ArrayList<>(entry.getOldPeersCount());
                for (final ByteString bstring : entry.getOldPeersList()) {
                    peers.add(JRaftUtils.getPeerId(AsciiStringUtil.unsafeDecode(bstring)));
                }
                log.setOldPeers(peers);
            }

            if (entry.getLearnersCount() > 0) {
                final List<PeerId> peers = new ArrayList<>(entry.getLearnersCount());
                for (final ByteString bstring : entry.getLearnersList()) {
                    peers.add(JRaftUtils.getPeerId(AsciiStringUtil.unsafeDecode(bstring)));
                }
                log.setLearners(peers);
            }

            if (entry.getOldLearnersCount() > 0) {
                final List<PeerId> peers = new ArrayList<>(entry.getOldLearnersCount());
                for (final ByteString bstring : entry.getOldLearnersList()) {
                    peers.add(JRaftUtils.getPeerId(AsciiStringUtil.unsafeDecode(bstring)));
                }
                log.setOldLearners(peers);
            }

            final ByteString data = entry.getData();
            if (!data.isEmpty()) {
                log.setData(ByteBuffer.wrap(ZeroByteStringHelper.getByteArray(data)));
            }

            return log;
        } catch (final InvalidProtocolBufferException e) {
            LOG.error("Fail to decode pb log entry", e);
            return null;
        }
    }

    private V2Decoder() {
    }
}
