package com.alipay.sofa.jraft.storage.io;

import com.alipay.sofa.jraft.rpc.ProtobufMsgFactory;
import com.alipay.sofa.jraft.util.Bits;
import com.google.protobuf.Message;

import java.io.*;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-04 12:36
 */
public class ProtoBufFile {
    static {

    }

    private final String path;

    public ProtoBufFile(String path) {
        this.path = path;
    }

    /**
     * 从文件中读取一条消息
     * Protobuf文件的构造 :
     * 4字节代表name长度 nameLen，接着有 nameLen 个字节的 name
     * 4字节代表消息长度 msgLen，接着有 msgLen 个字节的 msg
     * @param <T>
     */
    public <T extends Message> T load() throws Exception {
        File file = new File(this.path);
        if (!file.exists()) {
            return null;
        }
        final byte[] lenBytes = new byte[4];
        try (final FileInputStream fin = new FileInputStream(file);
             final BufferedInputStream input = new BufferedInputStream(fin)) {
            // 读取四个字节，并将其转为int类型整数: len
            readBytes(lenBytes, input);
            final int len = Bits.getInt(lenBytes, 0);
            if (len <= 0) {
                throw new IOException("Invalid message fullName.");
            }
            // 读取 len 个字节，并将其转为String，将其作为 Message 的 name
            final byte[] nameBytes = new byte[len];
            final String name = new String(nameBytes);
            // 再读取四个字节，将其转为 int 整数 : msgLen
            readBytes(lenBytes, input);
            final int msgLen = Bits.getInt(lenBytes, 0);
            final byte[] msgBytes = new byte[msgLen];
            readBytes(msgBytes, input);
            return ProtobufMsgFactory.newMessageByProtoClassName(name, msgBytes);
        }
    }

    private void readBytes(final byte[] bs, final InputStream input) throws Exception {
        int read = 0;
        if ((read = input.read(bs)) != bs.length) {
            throw new IOException("Read error, expects " + bs.length + " bytes, but read " + read);
        }
    }
}
