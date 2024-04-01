package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.util.Endpoint;
import com.google.protobuf.Message;

import java.util.concurrent.Future;

/**
 * @author : 小何
 * @Description : Raft客户端服务
 * @date : 2024-03-31 17:59
 */
public interface RaftClientService extends ClientService {
    /**
     * 预投票请求
     * @param endpoint
     * @param request
     * @param done
     * @return
     */
    Future<Message> preVote(final Endpoint endpoint,
                            final RpcRequests.RequestVoteRequest request,
                            final RpcResponseClosure<RpcRequests.RequestVoteResponse> done);

    /**
     * 投票请求
     * @param endpoint
     * @param request
     * @param done
     * @return
     */
    Future<Message> requestVote(final Endpoint endpoint,
                                final RpcRequests.RequestVoteRequest request,
                                final RpcResponseClosure<RpcRequests.RequestVoteResponse> done);

    /**
     * 同步日志请求
     * @param endpoint
     * @param request
     * @param timeoutMs
     * @param done
     * @return
     */
    Future<Message> appendEntries(final Endpoint endpoint,
                                  final RpcRequests.AppendEntriesRequest request,
                                  final int timeoutMs,
                                  final RpcResponseClosure<RpcRequests.AppendEntriesResponse> done);

    /**
     * 传输快照
     * @param endpoint
     * @param request
     * @param done
     * @return
     */
    Future<Message> installSnapshot(final Endpoint endpoint,
                                    final RpcRequests.InstallSnapshotRequest request,
                                    final RpcResponseClosure<RpcRequests.InstallSnapshotResponse> done);

    Future<Message> getFile(final Endpoint endpoint,
                            final RpcRequests.GetFileRequest request,
                            final int timeoutMs,
                            final RpcResponseClosure<RpcRequests.GetFileResponse> done);

    Future<Message> timeoutNow(final Endpoint endpoint,
                               final RpcRequests.TimeoutNowRequest request,
                               final int timeoutMs,
                               final RpcResponseClosure<RpcRequests.TimeoutNowResponse> done);

    Future<Message> readIndex(final Endpoint endpoint,
                              final RpcRequests.ReadIndexRequest request,
                              final int timeoutMs,
                              final RpcResponseClosure<RpcRequests.ReadIndexResponse> done);
}
