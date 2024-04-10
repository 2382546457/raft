package com.alipay.sofa.jraft.rpc.impl.core;

import com.alipay.remoting.Connection;
import com.alipay.remoting.ConnectionEventProcessor;
import com.alipay.sofa.jraft.ReplicatorGroup;
import com.alipay.sofa.jraft.entity.PeerId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-04 17:53
 */
public class ClientServiceConnectionEventProcessor implements ConnectionEventProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ClientServiceConnectionEventProcessor.class);
    private final ReplicatorGroup replicatorGroup;

    public ClientServiceConnectionEventProcessor(ReplicatorGroup replicatorGroup) {
        super();
        this.replicatorGroup = replicatorGroup;
    }

    @Override
    public void onEvent(String s, Connection connection) {
        final PeerId peer = new PeerId();
        if (peer.parse(s)) {
            logger.info("Peer {} is connected.", peer);
            this.replicatorGroup.checkReplicator(peer, true);
        } else {
            logger.error("Fail to parse peer: {}.", s);
        }
    }
}
