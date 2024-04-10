package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.rpc.impl.core.AppendEntriesRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.core.RequestVoteRequestProcessor;
import com.alipay.sofa.jraft.util.Endpoint;
import com.alipay.sofa.jraft.util.RpcFactoryHelper;

import java.util.concurrent.Executor;

/**
 * @author : 小何
 * @Description : 创建 Raft节点 RPC服务端 的工厂
 * @date : 2024-04-04 14:45
 */
public class RaftRpcServerFactory {
    static {
        ProtobufMsgFactory.load();
    }



    /**
     * 向 RpcServer 中添加非常重要的两个处理器 : AppendEntriesRequestProcessor、RequestVoteRequestProcessor
     * 同时，AppendEntriesRequestProcessor 又是一个 ConnectionClosedEventListener
     * @param rpcServer
     * @param raftExecutor
     * @param cliExecutor
     */
    public static void addRaftRequestProcessors(final RpcServer rpcServer,
                                                final Executor raftExecutor,
                                                final Executor cliExecutor) {
        final AppendEntriesRequestProcessor appendEntriesRequestProcessor = new AppendEntriesRequestProcessor(raftExecutor);
        rpcServer.registerConnectionClosedEventListener(appendEntriesRequestProcessor);
        rpcServer.registerProcessor(appendEntriesRequestProcessor);
        rpcServer.registerProcessor(new RequestVoteRequestProcessor(raftExecutor));
    }

    public static void addRaftRequestProcessors(final RpcServer server) {
        addRaftRequestProcessors(server, null, null);
    }

    /**
     * 根据 IP+PORT 创建一个 Raft节点的 RPC服务端
     * @param endpoint
     * @return
     */
    public static RpcServer createRaftRpcServer(final Endpoint endpoint) {
        return createRaftRpcServer(endpoint, null, null);
    }
    public static RpcServer createRaftRpcServer(final Endpoint endpoint,
                                                final Executor raftExecutor,
                                                final Executor cliExecutor) {
        final RpcServer rpcServer = RpcFactoryHelper.rpcFactory().createRpcServer(endpoint);
        addRaftRequestProcessors(rpcServer, raftExecutor, cliExecutor);
        return rpcServer;
    }

    public static RpcServer createAndStartRaftRpcServer(final Endpoint endpoint) {
        return createAndStartRaftRpcServer(endpoint, null, null);
    }

    /**
     * 创建并启动Raft节点的 RPC服务端
     * @param endpoint
     * @param raftExecutor
     * @param cliExecutor
     * @return
     */
    public static RpcServer createAndStartRaftRpcServer(final Endpoint endpoint, final Executor raftExecutor,
                                                        final Executor cliExecutor) {
        final RpcServer server = createRaftRpcServer(endpoint, raftExecutor, cliExecutor);
        server.init(null);
        return server;
    }
}
