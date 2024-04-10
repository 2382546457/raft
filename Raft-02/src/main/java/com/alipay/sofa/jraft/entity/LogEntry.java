package com.alipay.sofa.jraft.entity;

import com.alipay.sofa.jraft.util.CrcUtil;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * @author : 小何
 * @Description : 每一个 LogEntry 对应一个日志条目
 * @date : 2024-04-04 21:31
 */
public class LogEntry implements Checksum {
    public static final ByteBuffer EMPTY_DATA = ByteBuffer.wrap(new byte[0]);
    /**
     * 日志类型，可能是业务日志，也可能是配置变更日志
     */
    private EnumOutter.EntryType type;

    /**
     * 日志id
     */
    private LogId id = new LogId(0, 0);

    /**
     * 当前集群中的节点
     */
    private List<PeerId> peers;
    /**
     * 旧配置中的节点
     */
    private List<PeerId> oldPeers;
    /**
     * 当前集群中的学习者
     */
    private List<PeerId> learners;
    /**
     * 旧配置中的学习者
     */
    private List<PeerId> oldLearners;
    /**
     * 真正的配置信息
     */
    private ByteBuffer data = EMPTY_DATA;
    /**
     * 日志条目的校验和
     */
    private long checksum;
    /**
     * 是否需要校验
     */
    private boolean hasChecksum;




    public List<PeerId> getLearners() {
        return this.learners;
    }

    public void setLearners(final List<PeerId> learners) {
        this.learners = learners;
    }

    public List<PeerId> getOldLearners() {
        return this.oldLearners;
    }

    public void setOldLearners(final List<PeerId> oldLearners) {
        this.oldLearners = oldLearners;
    }

    public LogEntry() {
        super();
    }

    public LogEntry(final EnumOutter.EntryType type) {
        super();
        this.type = type;
    }

    public boolean hasLearners() {
        return (this.learners != null && !this.learners.isEmpty())
                || (this.oldLearners != null && !this.oldLearners.isEmpty());
    }

    @Override
    public long checkSum() {
        long c = checkSum(this.type.getNumber(), this.id.checkSum());
        c = checkSum(this.peers, c);
        c = checkSum(this.oldPeers, c);
        c = checkSum(this.learners, c);
        c = checkSum(this.oldLearners, c);
        if (this.data != null && this.data.hasRemaining()) {
            c = checkSum(c, CrcUtil.crc64(this.data));
        }
        return c;
    }
    public boolean hasChecksum() {
        return this.hasChecksum;
    }

    /**
     * 判断日志是否损坏，通过校验和判断
     * @return
     */
    public boolean isCorrupted() {
        return this.hasChecksum && this.checksum != checkSum();
    }
    public long getChecksum() {
        return this.checksum;
    }

    public void setChecksum(final long checksum) {
        this.checksum = checksum;
        this.hasChecksum = true;
    }

    public EnumOutter.EntryType getType() {
        return this.type;
    }

    public void setType(final EnumOutter.EntryType type) {
        this.type = type;
    }

    public LogId getId() {
        return this.id;
    }

    public void setId(final LogId id) {
        this.id = id;
    }

    public List<PeerId> getPeers() {
        return this.peers;
    }

    public void setPeers(final List<PeerId> peers) {
        this.peers = peers;
    }

    public List<PeerId> getOldPeers() {
        return this.oldPeers;
    }

    public void setOldPeers(final List<PeerId> oldPeers) {
        this.oldPeers = oldPeers;
    }

    public ByteBuffer getData() {
        return this.data;
    }


    public ByteBuffer sliceData() {
        return this.data != null ? this.data.slice() : null;
    }


    public ByteBuffer getReadOnlyData() {
        return this.data != null ? this.data.asReadOnlyBuffer() : null;
    }

    public void setData(final ByteBuffer data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "LogEntry [type=" + this.type + ", id=" + this.id + ", peers=" + this.peers + ", oldPeers="
                + this.oldPeers + ", learners=" + this.learners + ", oldLearners=" + this.oldLearners + ", data="
                + (this.data != null ? this.data.remaining() : 0) + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.data == null) ? 0 : this.data.hashCode());
        result = prime * result + ((this.id == null) ? 0 : this.id.hashCode());
        result = prime * result + ((this.learners == null) ? 0 : this.learners.hashCode());
        result = prime * result + ((this.oldLearners == null) ? 0 : this.oldLearners.hashCode());
        result = prime * result + ((this.oldPeers == null) ? 0 : this.oldPeers.hashCode());
        result = prime * result + ((this.peers == null) ? 0 : this.peers.hashCode());
        result = prime * result + ((this.type == null) ? 0 : this.type.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        LogEntry other = (LogEntry) obj;
        if (this.data == null) {
            if (other.data != null) {
                return false;
            }
        } else if (!this.data.equals(other.data)) {
            return false;
        }
        if (this.id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!this.id.equals(other.id)) {
            return false;
        }
        if (this.learners == null) {
            if (other.learners != null) {
                return false;
            }
        } else if (!this.learners.equals(other.learners)) {
            return false;
        }
        if (this.oldLearners == null) {
            if (other.oldLearners != null) {
                return false;
            }
        } else if (!this.oldLearners.equals(other.oldLearners)) {
            return false;
        }
        if (this.oldPeers == null) {
            if (other.oldPeers != null) {
                return false;
            }
        } else if (!this.oldPeers.equals(other.oldPeers)) {
            return false;
        }
        if (this.peers == null) {
            if (other.peers != null) {
                return false;
            }
        } else if (!this.peers.equals(other.peers)) {
            return false;
        }
        return this.type == other.type;
    }

}
