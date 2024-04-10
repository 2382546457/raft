package com.alipay.sofa.jraft.option;

import com.alipay.sofa.jraft.core.NodeImpl;

/**
 * @author : 小何
 * @Description : Raft节点元数据存储器的配置选项
 * @date : 2024-04-04 18:04
 */
public class RaftMetaStorageOptions {
    private NodeImpl node;

    public NodeImpl getNode() {
        return node;
    }

    public void setNode(NodeImpl node) {
        this.node = node;
    }
}
