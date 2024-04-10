package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.rpc.closure.RpcResponseClosure;
import com.alipay.sofa.jraft.util.Endpoint;
import com.google.protobuf.Message;

import java.util.concurrent.Future;

/**
 * @author : 小何
 * @Description : Raft客户端服务，提供的是具有 Raft节点特性的服务，比如预投票、投票、传输日志、安装快照
 * @date : 2024-04-04 14:41
 */
public interface RaftClientService extends ClientService {
    /**
     * 预投票
     * @param endpoint 对方IP+PORT
     * @param request 要发送的请求内容
     * @param done 回调
     * @return
     */
    Future<Message> preVote(final Endpoint endpoint,
                            final RpcRequests.RequestVoteRequest request,
                            final RpcResponseClosure<RpcRequests.RequestVoteResponse> done);

    /**
     * 正式投票
     * @param endpoint
     * @param request
     * @param done
     * @return
     */
    Future<Message> requestVote(final Endpoint endpoint,
                                final RpcRequests.RequestVoteRequest request,
                                final RpcResponseClosure<RpcRequests.RequestVoteResponse> done);

    /**
     * 发送消息
     * 可以是日志消息、心跳消息、探针消息
     * @param endpoint
     * @param request
     * @param timeoutMs
     * @param done
     * @return
     */
    Future<Message> appendEntries(final Endpoint endpoint, final RpcRequests.AppendEntriesRequest request,
                                  final int timeoutMs, final RpcResponseClosure<RpcRequests.AppendEntriesResponse> done);


    Future<Message> installSnapshot(final Endpoint endpoint, final RpcRequests.InstallSnapshotRequest request,
                                    final RpcResponseClosure<RpcRequests.InstallSnapshotResponse> done);

    Future<Message> getFile(final Endpoint endpoint, final RpcRequests.GetFileRequest request, final int timeoutMs,
                            final RpcResponseClosure<RpcRequests.GetFileResponse> done);


    Future<Message> timeoutNow(final Endpoint endpoint, final RpcRequests.TimeoutNowRequest request,
                               final int timeoutMs, final RpcResponseClosure<RpcRequests.TimeoutNowResponse> done);

    Future<Message> readIndex(final Endpoint endpoint, final RpcRequests.ReadIndexRequest request, final int timeoutMs,
                              final RpcResponseClosure<RpcRequests.ReadIndexResponse> done);
}
