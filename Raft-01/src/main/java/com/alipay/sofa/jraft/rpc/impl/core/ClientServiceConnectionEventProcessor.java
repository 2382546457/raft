package com.alipay.sofa.jraft.rpc.impl.core;

import com.alipay.remoting.Connection;
import com.alipay.remoting.ConnectionEventProcessor;
import com.alipay.sofa.jraft.ReplicatorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.ConnectionEventListener;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-03-31 21:37
 */
public class ClientServiceConnectionEventProcessor implements ConnectionEventProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ClientServiceConnectionEventProcessor.class);

    private final ReplicatorGroup rgGroup;
    public ClientServiceConnectionEventProcessor(ReplicatorGroup rgGroup) {
        super();
        this.rgGroup = rgGroup;
    }

    @Override
    public void onEvent(String s, Connection connection) {

    }
}
