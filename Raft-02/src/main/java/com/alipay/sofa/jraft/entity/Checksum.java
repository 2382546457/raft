package com.alipay.sofa.jraft.entity;

import java.util.Collection;

/**
 * @author : 小何
 * @Description : 校验和
 * @date : 2024-04-01 14:25
 */
public interface Checksum {
    long checkSum();

    public default long checkSum(final long v1, final long v2) {
        return v1 ^ v2;
    }
    public default long checkSum(final Collection<? extends Checksum> factors, long v) {
        if (factors != null && !factors.isEmpty()) {
            for (final Checksum factor : factors) {
                v = checkSum(v, factor.checkSum());
            }
        }
        return v;
    }
}
