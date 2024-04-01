package com.alipay.sofa.jraft.rpc;

import com.google.protobuf.Message;

/**
 * @author : 小何
 * @Description : Raft服务端的具体功能
 * @date : 2024-03-31 18:07
 */
public interface RaftServerService {
    /**
     * 处理预投票请求
     * @param request
     * @return
     */
    public Message handlePreVoteRequest(RpcRequests.RequestVoteRequest request);

    /**
     * 处理投票请求
     * @return
     */
    public Message handleRequestVoteRequest(RpcRequests.RequestVoteRequest request);

    /**
     * 处理日志和心跳的方法
     * @param request
     * @param done
     * @return
     */
    public Message handleAppendEntriesRequest(RpcRequests.AppendEntriesRequest request,
                                              RpcRequestClosure done);
}
