package com.alipay.sofa.jraft.entity;

import java.io.Serializable;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-05 11:14
 */
public class NodeId implements Serializable {
    private static final long serialVersionUID = 4428173460056804264L;
    /**
     * 节点所属的集群
     */
    private final String groupId;
    /**
     * 节点id
     */
    private final PeerId peerId;
    /**
     * 缓存 toString()
     */
    private String str;

    public NodeId(String groupId, PeerId peerId) {
        super();
        this.groupId = groupId;
        this.peerId = peerId;
    }

    public String getGroupId() {
        return this.groupId;
    }

    @Override
    public String toString() {
        if (str == null) {
            str = "<" + this.groupId + "/" + this.peerId + ">";
        }
        return str;
    }

    public PeerId getPeerId() {
        return this.peerId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.groupId == null ? 0 : this.groupId.hashCode());
        result = prime * result + (this.peerId == null ? 0 : this.peerId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NodeId other = (NodeId) obj;
        if (this.groupId == null) {
            if (other.groupId != null) {
                return false;
            }
        } else if (!this.groupId.equals(other.groupId)) {
            return false;
        }
        if (this.peerId == null) {
            return other.peerId == null;
        } else {
            return this.peerId.equals(other.peerId);
        }
    }
}
