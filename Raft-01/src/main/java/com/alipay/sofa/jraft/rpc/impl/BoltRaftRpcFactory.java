package com.alipay.sofa.jraft.rpc.impl;

import com.alipay.remoting.CustomSerializerManager;
import com.alipay.remoting.rpc.RpcConfigManager;
import com.alipay.remoting.rpc.RpcConfigs;
import com.alipay.sofa.jraft.rpc.ProtobufSerializer;
import com.alipay.sofa.jraft.rpc.RaftRpcFactory;
import com.alipay.sofa.jraft.rpc.RpcClient;
import com.alipay.sofa.jraft.rpc.RpcServer;
import com.alipay.sofa.jraft.util.Endpoint;
import com.alipay.sofa.jraft.util.LogThreadPoolExecutor;
import com.alipay.sofa.jraft.util.Requires;
import com.alipay.sofa.jraft.util.SystemPropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-03-31 21:06
 */
public class BoltRaftRpcFactory implements RaftRpcFactory {
    private static final Logger LOG = LoggerFactory.getLogger(BoltRaftRpcFactory.class);
    // 高低水位线，这两个成员变量肯定要设置到Netty中
    static final int CHANNEL_WRITE_BUF_LOW_WATER_MARK = SystemPropertyUtil.getInt(
            "bolt.channel_write_buf_low_water_mark",
            256 * 1024);
    static final int CHANNEL_WRITE_BUF_HIGH_WATER_MARK = SystemPropertyUtil.getInt(
            "bolt.channel_write_buf_high_water_mark",
            512 * 1024);

    @Override
    public void registerProtobufSerializer(String className, Object... args) {
        CustomSerializerManager.registerCustomSerializer(className, ProtobufSerializer.INSTANCE);
    }

    @Override
    public RpcClient createRpcClient(ConfigHelper<RpcClient> helper) {
        final com.alipay.remoting.rpc.RpcClient boltImpl = new com.alipay.remoting.rpc.RpcClient();
        final RpcClient rpcClient = new BoltRpcClient(boltImpl);
        if (helper != null) {
            helper.config(rpcClient);
        }
        return rpcClient;
    }

    /**
     * 创建服务端
     * @param endpoint 创建的 Server 绑定的端口号
     * @param helper
     * @return
     */
    @Override
    public RpcServer createRpcServer(Endpoint endpoint, ConfigHelper<RpcServer> helper) {
        final int port = Requires.requireNonNull(endpoint, "endpoint").getPort();
        Requires.requireTrue(port > 0 && port < 0xFFFF, "port out of range:" + port);
        final com.alipay.remoting.rpc.RpcServer boltImpl = new com.alipay.remoting.rpc.RpcServer(port, true, false);
        final RpcServer rpcServer = new BoltRpcServer(boltImpl);
        if (helper != null) {
            helper.config(rpcServer);
        }
        return rpcServer;
    }

    @Override
    public void ensurePipeline() {
        if (RpcConfigManager.dispatch_msg_list_in_default_executor()) {
            System.setProperty(RpcConfigs.DISPATCH_MSG_LIST_IN_DEFAULT_EXECUTOR, "false");
            LOG.warn("JRaft SET {} to be false for replicator pipeline optimistic.",
                    RpcConfigs.DISPATCH_MSG_LIST_IN_DEFAULT_EXECUTOR);
        }
    }
}
