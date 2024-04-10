package com.alipay.sofa.jraft.rpc.impl.core;

import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.rpc.RaftServerService;
import com.alipay.sofa.jraft.rpc.RpcRequestProcessor;
import com.alipay.sofa.jraft.rpc.closure.RpcRequestClosure;
import com.alipay.sofa.jraft.util.RpcFactoryHelper;
import com.google.protobuf.Message;
import com.alipay.sofa.jraft.NodeManager;
import java.util.concurrent.Executor;
import com.alipay.sofa.jraft.Node;
/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-05 11:07
 */
public abstract class NodeRequestProcessor<T extends Message> extends RpcRequestProcessor<T> {

    public NodeRequestProcessor(Executor executor, Message defaultResp) {
        super(executor, defaultResp);
    }
    protected abstract Message processRequest0(final RaftServerService serverService,
                                               final T request, final RpcRequestClosure done);

    protected abstract String getPeerId(final T request);
    protected abstract String getGroupId(final T request);
    @Override
    public Message processRequest(T request, RpcRequestClosure done) {
        final PeerId peer = new PeerId();
        final String peerIdStr = getPeerId(request);
        if (peer.parse(peerIdStr)) {
            final String groupId = getGroupId(request);
            final Node node = NodeManager.getInstance().get(groupId, peer);
            if (node != null) {
                return processRequest0((RaftServerService) node, request, done);
            } else {
                return RpcFactoryHelper.responseFactory().newResponse(defaultResp(), RaftError.ENOENT, "Peer id not found: %s, group: %s.", peerIdStr, groupId);
            }
        } else {
            return RpcFactoryHelper.responseFactory().newResponse(defaultResp(), RaftError.EINVAL, "Peer id not found: %s.", peerIdStr);
        }
    }
}
