package com.alipay.sofa.jraft.storage.impl;

import com.alipay.sofa.jraft.core.NodeImpl;
import com.alipay.sofa.jraft.core.NodeMetrics;
import com.alipay.sofa.jraft.entity.EnumOutter;
import com.alipay.sofa.jraft.entity.LocalStorageOutter;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.option.RaftMetaStorageOptions;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.storage.RaftMetaStorage;
import com.alipay.sofa.jraft.storage.io.ProtoBufFile;
import com.alipay.sofa.jraft.util.Utils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-04 19:43
 */
public class LocalRaftMetaStorage implements RaftMetaStorage {

    private static final Logger logger = LoggerFactory.getLogger(LocalRaftMetaStorage.class);
    /**
     * 元数据文件的名称
     */
    private static final String RAFT_META = "raft_meta";
    /**
     * 元数据文件是否已经初始化
     * 是否已经将内存的元数据存储到本地
     */
    private boolean isInited;

    /**
     * 元数据文件的路径
     */
    private final String path;
    /**
     * 任期
     */
    private long term;

    /**
     * 最后一次投票投给谁了
     */
    private PeerId voteFor = PeerId.emptyPeer();

    /**
     * Raft协议配置类
     */
    private final RaftOptions raftOptions;

    /**
     * 监控性能
     */
    private NodeMetrics nodeMetrics;
    /**
     * 当前节点
     */
    private NodeImpl node;


    public LocalRaftMetaStorage(final String path, final RaftOptions raftOptions) {
        super();
        this.path = path;
        this.raftOptions = raftOptions;
    }

    /**
     * 初始化方法，在 NodeImpl.init() 中调用，节点初始化时将元数据从本地文件加载到内存中
     * @param opts
     * @return
     */
    @Override
    public boolean init(RaftMetaStorageOptions opts) {
        if (this.isInited) {
            logger.warn("Raft meta storage is already inited.");
            return true;
        }
        this.node = opts.getNode();
        // 创建文件路径
        try {
            FileUtils.forceMkdir(new File(this.path));
        } catch (final IOException e) {
            logger.error("Fail to mkdir: {}.", this.path, e);
            return false;
        }
        // 从磁盘中加载数据
        if (load()) {
            this.isInited = true;
            return true;
        } else {
            return false;
        }
    }
    private boolean load() {
        final ProtoBufFile pbFile = newPbFile();
        try {
            // 将文件中的数据加载为 LocalStorageOutter.StablePBMeta 类型
            final LocalStorageOutter.StablePBMeta meta = pbFile.load();
            if (meta != null) {
                this.term = meta.getTerm();
                return this.voteFor.parse(meta.getVotedfor());
            }
            return true;
        } catch (final FileNotFoundException e) {
            //这 里跑出的是找不到文件的异常，就算抛这个异常，也返回true
            return true;
        } catch (final IOException e) {
            // 报出其他异常才返回false
            logger.error("Fail to load raft meta storage", e);
            return false;
        }
    }

    /**
     * 持久化元数据，将元数据从内存中保存到磁盘
     * @return
     */
    private boolean save() {
        final long start = Utils.monotonicMs();
        // 将元数据转换为 LocalStorageOutter.StablePBMeta
        final LocalStorageOutter.StablePBMeta meta = LocalStorageOutter.StablePBMeta.newBuilder()
                .setTerm(this.term)
                .setVotedfor(this.voteFor.toString())
                .build();
        final ProtoBufFile pbFile = newPbFile();
        try {
            // 将元数据持久化到磁盘, 默认同步
            if (!pbFile.save(meta, this.raftOptions.isSyncMeta())) {
                reportIOError();
                return false;
            }
            return true;
        } catch (final Exception e) {
            logger.error("Fail to save raft meta.", e);
            reportIOError();
            return false;
        } finally {
            final long cost = Utils.monotonicMs() - start;
            if (this.nodeMetrics != null) {
                this.nodeMetrics.recordLatency("save-raft-meta", cost);
            }
            logger.info("Save raft meta, path={}, term={}, votedFor={}, cost time={} ms.", this.path, this.term, this.voteFor, cost);
        }
    }
    private void reportIOError() {
        this.node.onError(new RaftException(EnumOutter.ErrorType.ERROR_TYPE_META, RaftError.EIO,
                "Fail to save raft meta, path=%s", this.path));
    }
    private ProtoBufFile newPbFile() {
        return new ProtoBufFile(this.path + File.separator + RAFT_META);
    }

    @Override
    public void shutdown() {
        if (!this.isInited) {
            return;
        }
        save();
        this.isInited = false;
    }

    private void checkState() {
        if (!this.isInited) {
            throw new IllegalStateException();
        }
    }
    @Override
    public boolean setTerm(long term) {
        checkState();
        this.term = term;
        return save();
    }

    @Override
    public long getTerm() {
        checkState();
        return this.term;
    }

    @Override
    public boolean setVoteFor(final PeerId peerId) {
        checkState();
        this.voteFor = peerId;
        return save();
    }

    @Override
    public PeerId getVoteFor() {
        checkState();
        return this.voteFor;
    }

    @Override
    public boolean setTermAndVoteFor(final long term, final PeerId peerId) {
        checkState();
        this.voteFor = peerId;
        this.term = term;
        return save();
    }
    @Override
    public String toString() {
        return "RaftMetaStorageImpl [path=" + this.path + ", term=" + this.term + ", votedFor=" + this.voteFor + "]";
    }
}
