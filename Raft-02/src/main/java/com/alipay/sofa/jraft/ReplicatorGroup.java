package com.alipay.sofa.jraft;

import com.alipay.sofa.jraft.core.ReplicatorType;
import com.alipay.sofa.jraft.entity.NodeId;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.ReplicatorGroupOptions;
import com.alipay.sofa.jraft.util.Describer;
import com.alipay.sofa.jraft.util.ThreadId;

/**
 * 复制器组
 */
public interface ReplicatorGroup extends Describer {
    boolean init(final NodeId nodeId, final ReplicatorGroupOptions opts);
    default boolean addReplicator(final PeerId peer) {
        return addReplicator(peer, ReplicatorType.Follower);
    }
    default boolean addReplicator(final PeerId peer, ReplicatorType replicatorType) {
        return addReplicator(peer, replicatorType, true);
    }

    boolean addReplicator(final PeerId peer, ReplicatorType replicatorType, boolean sync);

    ThreadId getReplicator(final PeerId peer);

    void checkReplicator(final PeerId peer, final boolean lockNode);

    boolean resetTerm(final long newTerm);

    boolean contains(final PeerId peer);

}
