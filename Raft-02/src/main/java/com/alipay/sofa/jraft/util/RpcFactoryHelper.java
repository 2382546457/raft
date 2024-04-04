package com.alipay.sofa.jraft.util;

import com.alipay.sofa.jraft.rpc.RaftRpcFactory;
import com.alipay.sofa.jraft.rpc.RpcResponseFactory;

/**
 * @author : 小何
 * @Description : 拥有 RaftRpcFactory、RpcResponseFactory 的Helper类
 * @date : 2024-04-04 13:52
 */
public class RpcFactoryHelper {
    private static final RaftRpcFactory RPC_FACTORY = JRaftServiceLoader.load(RaftRpcFactory.class).first();

    public static RaftRpcFactory rpcFactory() {
        return RPC_FACTORY;
    }
    public static RpcResponseFactory responseFactory() {
        return RPC_FACTORY.getRpcResponseFactory();
    }
}
