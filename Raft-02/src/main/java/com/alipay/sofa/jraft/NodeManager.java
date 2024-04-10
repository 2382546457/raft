package com.alipay.sofa.jraft;

import com.alipay.sofa.jraft.entity.NodeId;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.util.Endpoint;
import com.alipay.sofa.jraft.util.Utils;
import com.alipay.sofa.jraft.util.concurrent.ConcurrentHashSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author : 小何
 * @Description : 节点管理器，管理当前进程所有的节点
 * @date : 2024-04-05 11:16
 */
public class NodeManager {
    private static final NodeManager INSTANCE = new NodeManager();
    public static NodeManager getInstance() {
        return INSTANCE;
    }
    private NodeManager() {
    }

    /**
     * key : 节点ID
     * value : 节点
     */
    private final ConcurrentMap<NodeId, Node> nodeMap = new ConcurrentHashMap<>();
    /**
     * 存储集群和集群内的节点
     */
    private final ConcurrentMap<String, List<Node>> groupMap = new ConcurrentHashMap<>();
    /**
     * 存放程序中所有节点的地址
     */
    private final ConcurrentHashSet<Endpoint> addrSet = new ConcurrentHashSet<>();

    /**
     * 判断某地址是否已经存在
     * @param addr
     * @return
     */
    public boolean serverExists(final Endpoint addr) {
        if (addr.getIp().equals(Utils.IP_ANY)) {
            return this.addrSet.contains(new Endpoint(Utils.IP_ANY, addr.getPort()));
        }
        return this.addrSet.contains(addr);
    }


    /**
     * 从addrSet集合移除一个地址
     * @param addr
     * @return
     */
    public boolean removeAddress(final Endpoint addr) {
        return this.addrSet.remove(addr);
    }

    /**
     * 向addrSet集合中添加节点的地址
     * @param addr
     */
    public void addAddress(final Endpoint addr) {
        this.addrSet.add(addr);
    }

    /**
     * 添加新节点
     * @param node
     * @return
     */
    public boolean add(final Node node) {
        // 如果节点已经存在，添加失败
        if (!serverExists(node.getNodeId().getPeerId().getEndpoint())) {
            return false;
        }
        final NodeId nodeId = node.getNodeId();
        // 如果成功添加说明之前没有
        if (this.nodeMap.putIfAbsent(nodeId, node) == null) {
            final String groupId = node.getGroupId();
            List<Node> nodes = groupMap.get(groupId);
            if (nodes == null) {
                nodes = Collections.synchronizedList(new ArrayList<>());
                List<Node> existsNode = this.groupMap.putIfAbsent(groupId, nodes);
                if (existsNode != null){
                    nodes = existsNode;
                }
            }
            nodes.add(node);
            return true;
        }
        return false;
    }


    public boolean remove(final Node node) {
        if (this.nodeMap.remove(node.getNodeId(), node)) {
            final List<Node> nodes = this.groupMap.get(node.getGroupId());
            if (nodes != null) {
                return nodes.remove(node);
            }
        }
        return false;
    }


    public Node get(final String groupId, final PeerId peerId) {
        return this.nodeMap.get(new NodeId(groupId, peerId));
    }


    public List<Node> getNodesByGroupId(final String groupId) {
        return this.groupMap.get(groupId);
    }


    public List<Node> getAllNodes() {
        return this.groupMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
    }


}
