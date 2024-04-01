package com.alipay.sofa.jraft.storage.io;

import com.alipay.sofa.jraft.rpc.ProtobufMsgFactory;
import com.google.protobuf.Message;
import com.alipay.sofa.jraft.util.Bits;
import java.io.*;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-03-31 15:51
 */
public class ProtoBufFile {
    static {
        ProtobufMsgFactory.load();
    }
    private final String path;

    public ProtoBufFile(String path) {
        this.path = path;
    }

    /**
     * 从文件中读取一条 protobuf 消息
     * @return
     * @param <T>
     * @throws Exception
     */
    public <T extends Message> T load() throws IOException {
        File file = new File(this.path);

        if (!file.exists()) {
            return null;
        }

        final byte[] lenBytes = new byte[4];
        try (final FileInputStream fin = new FileInputStream(file);
             final BufferedInputStream input = new BufferedInputStream(fin)) {
            readBytes(lenBytes, input);
            final int len = Bits.getInt(lenBytes, 0);
            if (len <= 0) {
                throw new IOException("Invalid message fullName.");
            }
            final byte[] nameBytes = new byte[len];
            readBytes(nameBytes, input);
            final String name = new String(nameBytes);
            readBytes(lenBytes, input);
            final int msgLen = Bits.getInt(lenBytes, 0);
            final byte[] msgBytes = new byte[msgLen];
            readBytes(msgBytes, input);
            return ProtobufMsgFactory.newMessageByProtoClassName(name, msgBytes);
        }
    }

    private void readBytes(final byte[] bs, final InputStream input) throws IOException {
        int read;
        if ((read = input.read(bs)) != bs.length) {
            throw new IOException("Read error, expects " + bs.length + " bytes, but read " + read);
        }
    }
}
