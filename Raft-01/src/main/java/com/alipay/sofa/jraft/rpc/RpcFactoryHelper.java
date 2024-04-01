package com.alipay.sofa.jraft.rpc;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-03-31 15:45
 */
public class RpcFactoryHelper {
//    private static final RaftRpcFactory RPC_FACTORY = JRaftServiceLoader.load(RaftRpcFactory.class).first();

    public static RaftRpcFactory rpcFactory() {
//        return RPC_FACTORY;
        return null;
    }
    public static RpcResponseFactory responseFactory() {
        return null;
    }
}
