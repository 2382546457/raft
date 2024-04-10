package com.alipay.sofa.jraft.rpc.impl.core;

import com.alipay.sofa.jraft.rpc.RaftServerService;
import com.alipay.sofa.jraft.rpc.RpcRequests;
import com.alipay.sofa.jraft.rpc.closure.RpcRequestClosure;
import com.google.protobuf.Message;

import java.util.concurrent.Executor;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-06 15:35
 */
public class RequestVoteRequestProcessor extends NodeRequestProcessor<RpcRequests.RequestVoteRequest>{
    public RequestVoteRequestProcessor(Executor executor) {
        super(executor, RpcRequests.RequestVoteResponse.getDefaultInstance());
    }

    @Override
    public String interest() {
        return null;
    }

    @Override
    public Message processRequest0(final RaftServerService service, final RpcRequests.RequestVoteRequest request,
                                   final RpcRequestClosure done) {
        // 如果是预选举，就进入下面的分支
        if (request.getPreVote()) {
            return service.handlePreVoteRequest(request);
        } else {
            // 走到这里就意味着是正式选举活动
            return service.handleRequestVoteRequest(request);
        }
    }

    @Override
    protected String getPeerId(final RpcRequests.RequestVoteRequest request) {
        return request.getPeerId();
    }

    @Override
    protected String getGroupId(final RpcRequests.RequestVoteRequest request) {
        return request.getGroupId();
    }

}
