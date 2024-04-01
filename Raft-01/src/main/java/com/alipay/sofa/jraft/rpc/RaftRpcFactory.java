package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.option.RpcOptions;
import com.alipay.sofa.jraft.util.Endpoint;

/**
 * @author : 小何
 * @Description : 创建 Raft 客户端和服务端的工厂
 * @date : 2024-03-31 15:44
 */
public interface RaftRpcFactory {
    interface ConfigHelper<T> {

        void config(final T instance);
    }
    RpcResponseFactory DEFAULT = new RpcResponseFactory() {};


    //注册protobuf序列化器
    void registerProtobufSerializer(final String className, final Object... args);
    public default RpcClient createRpcClient() {
        return createRpcClient(null);
    }

    /**
     * 创建 RPC 客户端
     * @param helper
     * @return
     */
    public RpcClient createRpcClient(final ConfigHelper<RpcClient> helper);

    default RpcServer createRpcServer(final Endpoint endpoint) {
        return createRpcServer(endpoint, null);
    }

    //创建RPC服务端
    public RpcServer createRpcServer(final Endpoint endpoint, final ConfigHelper<RpcServer> helper);


    public default RpcResponseFactory getRpcResponseFactory() {
        return DEFAULT;
    }

    /**
     * 是否启用复制器对象的 pipeline 功能
     * @return
     */
    default boolean isReplicatorPipelineEnabled() {
        return true;
    }
    //确保RPC框架支持Pipeline功能
    default void ensurePipeline() {}

    @SuppressWarnings("unused")
    default ConfigHelper<RpcClient> defaultJRaftClientConfigHelper(final RpcOptions opts) {
        return null;
    }

    @SuppressWarnings("unused")
    default ConfigHelper<RpcServer> defaultJRaftServerConfigHelper(final RpcOptions opts) {
        return null;
    }
}
