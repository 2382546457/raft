package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.option.RpcOptions;
import com.alipay.sofa.jraft.util.Endpoint;

/**
 * @author : 小何
 * @Description : 创建 Raft 集群中客户端和服务端的工厂
 * @date : 2024-04-04 14:29
 */
public interface RaftRpcFactory {
    /**
     * 默认客户端ConfigHelper
     * @param opts
     * @return
     */
    @SuppressWarnings("unused")
    default ConfigHelper<RpcClient> defaultJRaftClientConfigHelper(final RpcOptions opts) {
        return null;
    }

    /**
     * 默认服务端Helper
     * @param opts
     * @return
     */
    @SuppressWarnings("unused")
    default ConfigHelper<RpcServer> defaultJRaftServerConfigHelper(final RpcOptions opts) {
        return null;
    }
    interface ConfigHelper<T> {
        void config(final T instance);
    }
    /**
     * 根据给定的内容，创建响应对象的工厂
     */
    RpcResponseFactory DEFAULT = new RpcResponseFactory() {
    };

    /**
     * 注册protobuf序列化器
     */
    void registerProtobufSerializer(final String className, final Object... args);

    /**
     * 确保RPC框架支持Pipeline功能
     */
    default void ensurePipeline() {}

    /**
     * 是否启用复制器对象的Pipeline功能，这个功能是日志复制时非常重要的一个知识
     * @return
     */
    default boolean isReplicatorPipelineEnabled() {
        return true;
    }

    default RpcClient createRpcClient() {
        return createRpcClient(null);
    }
    RpcClient createRpcClient(final ConfigHelper<RpcClient> helper);

    default RpcServer createRpcServer(final Endpoint endpoint) {
        return createRpcServer(endpoint, null);
    }
    RpcServer createRpcServer(final Endpoint endpoint, final ConfigHelper<RpcServer> helper);
    default RpcResponseFactory getRpcResponseFactory() {
        return DEFAULT;
    }

}
