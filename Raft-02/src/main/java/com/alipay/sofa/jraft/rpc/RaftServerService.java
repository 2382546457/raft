package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.rpc.closure.RpcRequestClosure;
import com.google.protobuf.Message;

/**
 * @author : 小何
 * @Description : Raft 服务端的service
 * @date : 2024-04-04 16:16
 */
public interface RaftServerService {
    /**
     * 作为服务端，处理预投票请求的方法
     * @param request
     * @return
     */
    Message handlePreVoteRequest(RpcRequests.RequestVoteRequest request);

    /**
     * 作为服务端，处理投票请求的方法
     * @param request
     * @return
     */
    Message handleRequestVoteRequest(RpcRequests.RequestVoteRequest request);

    /**
     * 作为服务端，处理日志和心跳的方法
     * @param request
     * @param done
     * @return
     */
    Message handleAppendEntriesRequest(RpcRequests.AppendEntriesRequest request, RpcRequestClosure done);

}
