package com.alipay.sofa.jraft.storage;

import com.alipay.sofa.jraft.Lifecycle;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.RaftMetaStorageOptions;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-04 18:04
 */
public interface RaftMetaStorage extends Lifecycle<RaftMetaStorageOptions>, Storage {
    /**
     * 设置任期
     * @param term
     * @return
     */
    public boolean setTerm(final long term);

    /**
     * 获取任期
     * @return
     */
    public long getTerm();

    /**
     * 将选票投给了谁
     * @param peerId
     * @return
     */
    public boolean setVoteFor(final PeerId peerId) ;

    public PeerId getVoteFor();

    public boolean setTermAndVoteFor(final long term, final PeerId peerId);
}
