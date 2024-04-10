package com.alipay.sofa.jraft.rpc.impl;

import com.alipay.remoting.CustomSerializerManager;
import com.alipay.remoting.rpc.RpcConfigManager;
import com.alipay.remoting.rpc.RpcConfigs;
import com.alipay.sofa.jraft.rpc.ProtobufSerializer;
import com.alipay.sofa.jraft.rpc.RaftRpcFactory;
import com.alipay.sofa.jraft.rpc.RpcClient;
import com.alipay.sofa.jraft.rpc.RpcServer;
import com.alipay.sofa.jraft.util.Endpoint;
import com.alipay.sofa.jraft.util.Requires;
import com.alipay.sofa.jraft.util.SystemPropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-04 17:33
 */
public class BoltRaftRpcFactory implements RaftRpcFactory {
    private static final Logger LOG = LoggerFactory.getLogger(BoltRaftRpcFactory.class);
    /**
     * 高低水位线，这两个成员变量肯定要设置到Netty中
     */
    static final int CHANNEL_WRITE_BUF_LOW_WATER_MARK = SystemPropertyUtil.getInt(
            "bolt.channel_write_buf_low_water_mark",
            256 * 1024);
    static final int CHANNEL_WRITE_BUF_HIGH_WATER_MARK = SystemPropertyUtil.getInt(
            "bolt.channel_write_buf_high_water_mark",
            512 * 1024);


    @Override
    public void registerProtobufSerializer(final String className, final Object... args) {
        CustomSerializerManager.registerCustomSerializer(className, ProtobufSerializer.INSTANCE);
    }

    //创建客户端
    @Override
    public RpcClient createRpcClient(final RaftRpcFactory.ConfigHelper<RpcClient> helper) {
        // 先创建了bolt框架中的客户端
        final com.alipay.remoting.rpc.RpcClient boltImpl = new com.alipay.remoting.rpc.RpcClient();
        // 把真正的客户端交给BoltRpcClient对象使用，在sofajraft框架中，使用的则是BoltRpcClient对象
        // 但是BoltRpcClient肯定也只是起到一个代理的作用，在BoltRpcClient对象内部会调用bolt框架的客户端来发送请求
        final RpcClient rpcClient = new BoltRpcClient(boltImpl);
        if (helper != null) {
            helper.config(rpcClient);
        }
        return rpcClient;
    }

    //创建服务端
    @Override
    public RpcServer createRpcServer(final Endpoint endpoint, final RaftRpcFactory.ConfigHelper<RpcServer> helper) {
        final int port = Requires.requireNonNull(endpoint, "endpoint").getPort();
        Requires.requireTrue(port > 0 && port < 0xFFFF, "port out of range:" + port);
        //创建bolt框架的服务端，也就是真正具备通信功能的服务端
        final com.alipay.remoting.rpc.RpcServer boltImpl = new com.alipay.remoting.rpc.RpcServer(port, true, false);
        //把bolt框架的服务端交给BoltRpcServer对象使用，在sofajraft框架中，使用的则是BoltRpcServer对象
        final RpcServer rpcServer = new BoltRpcServer(boltImpl);
        if (helper != null) {
            helper.config(rpcServer);
        }
        return rpcServer;
    }

    @Override
    public void ensurePipeline() {
        // enable `bolt.rpc.dispatch-msg-list-in-default-executor` system property
        if (RpcConfigManager.dispatch_msg_list_in_default_executor()) {
            System.setProperty(RpcConfigs.DISPATCH_MSG_LIST_IN_DEFAULT_EXECUTOR, "false");
            LOG.warn("JRaft SET {} to be false for replicator pipeline optimistic.",
                    RpcConfigs.DISPATCH_MSG_LIST_IN_DEFAULT_EXECUTOR);
        }
    }
}
