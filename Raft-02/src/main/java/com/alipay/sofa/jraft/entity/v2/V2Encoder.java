package com.alipay.sofa.jraft.entity.v2;

import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.codec.LogEntryEncoder;
import com.alipay.sofa.jraft.error.LogEntryCorruptedException;
import com.alipay.sofa.jraft.util.AsciiStringUtil;
import com.alipay.sofa.jraft.util.Requires;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.ZeroByteStringHelper;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/11/30
 * @Description:日志的编码器对象
 */
public class V2Encoder implements LogEntryEncoder {

    public static final V2Encoder INSTANCE = new V2Encoder();

    private static boolean hasPeers(final Collection<PeerId> peers) {
        return peers != null && !peers.isEmpty();
    }

    private void encodePeers(final LogOutter.PBLogEntry.Builder builder, final List<PeerId> peers) {
        final int size = peers.size();
        for (int i = 0; i < size; i++) {
            builder.addPeers(ZeroByteStringHelper.wrap(AsciiStringUtil.unsafeEncode(peers.get(i).toString())));
        }
    }

    private void encodeOldPeers(final LogOutter.PBLogEntry.Builder builder, final List<PeerId> peers) {
        final int size = peers.size();
        for (int i = 0; i < size; i++) {
            builder.addOldPeers(ZeroByteStringHelper.wrap(AsciiStringUtil.unsafeEncode(peers.get(i).toString())));
        }
    }

    private void encodeLearners(final LogOutter.PBLogEntry.Builder builder, final List<PeerId> learners) {
        final int size = learners.size();
        for (int i = 0; i < size; i++) {
            builder.addLearners(ZeroByteStringHelper.wrap(AsciiStringUtil.unsafeEncode(learners.get(i).toString())));
        }
    }

    private void encodeOldLearners(final LogOutter.PBLogEntry.Builder builder, final List<PeerId> learners) {
        final int size = learners.size();
        for (int i = 0; i < size; i++) {
            builder.addOldLearners(ZeroByteStringHelper.wrap(AsciiStringUtil.unsafeEncode(learners.get(i).toString())));
        }
    }

    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/12/1
     * @Description:编码方法，这个是和解码方法一一对应的，就不再一一解释了，逻辑非常简单
     * 当需要把日志存放到数据库时，就会调用这个编码方法，将LogEntry对象进行编码，然后落盘
     */
    @Override
    public byte[] encode(final LogEntry log) {
        Requires.requireNonNull(log, "Null log");
        final LogId logId = log.getId();
        //日志中的各种信息最终都会先编码，然后设置到PBLogEntry中
        final LogOutter.PBLogEntry.Builder builder = LogOutter.PBLogEntry.newBuilder()
                .setType(log.getType())
                .setIndex(logId.getIndex())
                .setTerm(logId.getTerm());
        final List<PeerId> peers = log.getPeers();
        if (hasPeers(peers)) {
            encodePeers(builder, peers);
        }

        final List<PeerId> oldPeers = log.getOldPeers();
        if (hasPeers(oldPeers)) {
            encodeOldPeers(builder, oldPeers);
        }

        final List<PeerId> learners = log.getLearners();
        if (hasPeers(learners)) {
            encodeLearners(builder, learners);
        }
        final List<PeerId> oldLearners = log.getOldLearners();
        if (hasPeers(oldLearners)) {
            encodeOldLearners(builder, oldLearners);
        }
        if (log.hasChecksum()) {
            builder.setChecksum(log.getChecksum());
        }
        builder.setData(log.getData() != null ? ZeroByteStringHelper.wrap(log.getData()) : ByteString.EMPTY);
        //在这里终于创建出了PBLogEntry对象
        final LogOutter.PBLogEntry pbLogEntry = builder.build();
        //获取该对象序列化后的大小
        final int bodyLen = pbLogEntry.getSerializedSize();
        //创建字节数组，这个字节数组最终会存放所有数据
        //LogEntryV2CodecFactory.HEADER_SIZE 这个得到的是编码协议中魔术，版本号，预留字段的长度
        final byte[] ret = new byte[LogEntryV2CodecFactory.HEADER_SIZE + bodyLen];
        //设置魔术
        int i = 0;
        for (; i < LogEntryV2CodecFactory.MAGIC_BYTES.length; i++) {
            ret[i] = LogEntryV2CodecFactory.MAGIC_BYTES[i];
        }//设置版本号
        ret[i++] = LogEntryV2CodecFactory.VERSION;
        //设置预留字段
        for (; i < LogEntryV2CodecFactory.HEADER_SIZE; i++) {
            ret[i] = LogEntryV2CodecFactory.RESERVED[i - LogEntryV2CodecFactory.MAGIC_BYTES.length - 1];
        }
        //把pbLogEntry对象写入到ret字节数组中
        writeToByteArray(pbLogEntry, ret, i, bodyLen);
        //返回编码完成的字节数组
        return ret;
    }

    //把pbLogEntry对象写入到ret字节数组的方法
    private void writeToByteArray(final LogOutter.PBLogEntry pbLogEntry, final byte[] array, final int offset, final int len) {
        final CodedOutputStream output = CodedOutputStream.newInstance(array, offset, len);
        try {
            pbLogEntry.writeTo(output);
            output.checkNoSpaceLeft();
        } catch (final IOException e) {
            throw new LogEntryCorruptedException(
                    "Serializing PBLogEntry to a byte array threw an IOException (should never happen).", e);
        }
    }

    private V2Encoder() {
    }
}
