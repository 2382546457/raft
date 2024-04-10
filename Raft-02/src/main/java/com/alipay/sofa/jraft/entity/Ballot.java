package com.alipay.sofa.jraft.entity;

import com.alipay.sofa.jraft.conf.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : 小何
 * @Description : 投票计数器
 * @date : 2024-04-05 20:02
 */
public class Ballot {
    /**
     * peer在集合中的位置，方便快速查找
     */
    public static final class PosHint {
        // 在 peers 中的位置
        int pos0 = -1;
        // 在 oldPeers 中的位置
        int pos1 = -1;
    }

    public static class UnfoundPeerId {
        PeerId peerId;
        boolean found;
        int index;

        public UnfoundPeerId(PeerId peerId, int index, boolean found) {
            super();
            this.peerId = peerId;
            this.found = found;
            this.index = index;
        }
    }

    private final List<UnfoundPeerId> peers = new ArrayList<>();
    private final List<UnfoundPeerId> oldPeers = new ArrayList<>();
    /**
     * 当前集群需要收到的最小票数 （过半机制）集群节点数量 / 2 + 1
     */
    private int quorum;
    /**
     * 旧配置中需要收到的最小票数
     */
    private int oldQuorum;

    public boolean init(Configuration conf, Configuration oldConf) {
        // 清空之前的配置
        this.peers.clear();
        this.oldPeers.clear();
        this.oldQuorum = this.quorum = 0;
        int index = 0;
        // 将配置中的 Peer 节点添加到 peers 中
        if (conf != null) {
            for (final PeerId peer : conf) {
                this.peers.add(new UnfoundPeerId(peer, index++, false));
            }
        }
        this.quorum = this.peers.size() / 2 + 1;
        index = 0;
        //如果旧配置不为空，则执行和上面相同的逻辑
        for (final PeerId peer : oldConf) {
            this.oldPeers.add(new UnfoundPeerId(peer, index++, false));
        }
        this.oldQuorum = this.oldPeers.size() / 2 + 1;
        return true;
    }

    public void grant(final PeerId peerId) {
        grant(peerId, new PosHint());
    }
    /**
     * Peer节点 给当前节点投票
     * @param peerId
     * @param hint
     * @return
     */
    public PosHint grant(final PeerId peerId, final PosHint hint) {
        // 在 peers 中寻找这个节点找到了就可以进行投票
        UnfoundPeerId peer = findPeer(peerId, this.peers, hint.pos0);
        if (peer != null) {
            if (!peer.found) {
                peer.found = true;
                this.quorum --;
            }
            hint.pos0 = peer.index;
        } else {
            hint.pos0 = -1;
        }
        //判断一下旧配置的节点集合是否为空
        if (this.oldPeers.isEmpty()) {
            hint.pos1 = -1;
            return hint;
        }
        //如果不为空，就执行和上面相同的逻辑，这里的当前配置和旧配置先不必关心
        peer = findPeer(peerId, this.oldPeers, hint.pos1);
        if (peer != null) {
            if (!peer.found) {
                peer.found = true;
                this.oldQuorum--;
            }
            hint.pos1 = peer.index;
        } else {
            hint.pos1 = -1;
        }
        return hint;
    }
    private UnfoundPeerId findPeer(final PeerId peerId, List<UnfoundPeerId> peers, int posHint) {
        // 如果下标越界或者对应的下标与 peerId不相等, 去集合中找
        if (posHint < 0 || posHint >= peers.size() || !peers.get(posHint).peerId.equals(peerId)) {
            for (UnfoundPeerId ufp : peers) {
                if (ufp.peerId.equals(peerId)) {
                    return ufp;
                }
            }
            return null;
        }
        return peers.get(posHint);
    }

    /**
     * 判断是否收到了足够的票数
     * @return
     */
    public boolean isGranted() {
        return this.quorum <= 0 && this.oldQuorum <= 0;
    }
}
