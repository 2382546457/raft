package com.alipay.sofa.jraft;

import com.alipay.sofa.jraft.entity.NodeId;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.option.RaftOptions;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-04 18:03
 */
public interface Node extends Lifecycle<NodeOptions> {
    PeerId getLeaderId();


    NodeId getNodeId();

    String getGroupId();

    RaftOptions getRaftOptions();
}
