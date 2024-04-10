package com.alipay.sofa.jraft.util;

import com.alipay.sofa.jraft.util.internal.UnsafeUtil;
import com.google.protobuf.ByteString;

//String编解码工具类
public final class AsciiStringUtil {

    public static byte[] unsafeEncode(final CharSequence in) {
        final int len = in.length();
        final byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) in.charAt(i);
        }
        return out;
    }

    public static String unsafeDecode(final byte[] in, final int offset, final int len) {
        final char[] out = new char[len];
        for (int i = 0; i < len; i++) {
            out[i] = (char) (in[i + offset] & 0xFF);
        }
        return UnsafeUtil.moveToString(out);
    }

    public static String unsafeDecode(final byte[] in) {
        return unsafeDecode(in, 0, in.length);
    }

    public static String unsafeDecode(final ByteString in) {
        final int len = in.size();
        final char[] out = new char[len];
        for (int i = 0; i < len; i++) {
            out[i] = (char) (in.byteAt(i) & 0xFF);
        }
        return UnsafeUtil.moveToString(out);
    }

    private AsciiStringUtil() {
    }
}
