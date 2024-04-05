package com.alipay.sofa.jraft.conf;

import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.util.Copiable;
import com.alipay.sofa.jraft.util.Requires;
import io.netty.util.internal.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-04 22:06
 */
public class Configuration implements Iterable<PeerId>, Copiable<Configuration> {
    private static final Logger logger = LoggerFactory.getLogger(Configuration.class);
    /**
     * 判断新加进来的节点是否为学习者
     * 学习者只需要从领导者那里同步数据，不参与投票和决策
     */
    private static final String LEARNER_POSTFIX = "/learner";
    /**
     * 集群中的所有节点
     */
    private List<PeerId> peers = new ArrayList<>();
    /**
     * 集群中新加进来的所有学习者
     */
    private LinkedHashSet<PeerId> learners = new LinkedHashSet<>();

    public Configuration(final Iterable<PeerId> conf) {
        this(conf, null);
    }

    public Configuration() {
        super();
    }
    public Configuration(final Configuration conf) {
        this(conf.getPeers(), conf.getLearners());
    }

    public Configuration(final Iterable<PeerId> conf, final Iterable<PeerId> learners) {
        Requires.requireNonNull(conf, "conf");
        for (final PeerId peer : conf) {
            this.peers.add(peer.copy());
        }
        addLearners(learners);
    }

    /**
     * 将用户以输入的命令解析为 Configuration
     * 如 127.0.0.1:8081,127.0.0.1:8082,127.0.0.1:8083
     * @param conf
     * @return
     */
    public boolean parse(final String conf) {
        if (StringUtils.isBlank(conf)) {
            return false;
        }
        reset();
        String[] peerStrs = StringUtils.split(conf, ",");
        for (String peerStr : peerStrs) {
            PeerId peer = new PeerId();
            int index;
            boolean isLearner = false;
            // 如果正在解析的IP地址中包含 "/learner"，说明这是一个学习者
            if ((index = peerStr.indexOf(LEARNER_POSTFIX)) > 0) {
                peerStr = peerStr.substring(0, index);
                isLearner = true;
            }
            if (peer.parse(peerStr)) {
                if (isLearner) {
                    addLearner(peer);
                } else {
                    addPeer(peer);
                }
            } else {
                logger.error("Fail to parse peer {} in {}, ignore it.", peerStr, conf);
            }
        }
        return true;
    }
    public int addLearners(final Iterable<PeerId> learners) {
        int ret = 0;
        if (learners != null) {
            for (final PeerId peer : learners) {
                if (this.learners.add(peer.copy())) {
                    ret++;
                }
            }
        }
        return ret;
    }
    public boolean removeLearner(final PeerId learner) {
        return this.learners.remove(learner);
    }
    public List<PeerId> listLearners() {
        return new ArrayList<>(this.learners);
    }

    @Override
    public Configuration copy() {
        return new Configuration(this.peers, this.learners);
    }

    /**
     * 判断当前配置是否有效, 一个节点的不能同时在 peers 中和 learners 中
     * @return
     */
    public boolean isValid() {
        // 先获取集群中的 peerId
        final Set<PeerId> intersection = new HashSet<>(this.peers);
        intersection.retainAll(this.learners);
        return !this.peers.isEmpty() && intersection.isEmpty();
    }

    @Override
    public Iterator<PeerId> iterator() {
        return this.peers.iterator();
    }
    public void reset() {
        this.peers.clear();
        this.learners.clear();
    }
    public boolean addLearner(final PeerId learner) {
        return this.learners.add(learner);
    }

    public boolean isEmpty() {
        return this.peers.isEmpty();
    }

    public int size() {
        return this.peers.size();
    }

    public List<PeerId> getPeers() {
        return this.peers;
    }
    public LinkedHashSet<PeerId> getLearners() {
        return this.learners;
    }
    public void appendPeers(final Collection<PeerId> set) {
        this.peers.addAll(set);
    }

    public void setPeers(final List<PeerId> peers) {
        this.peers.clear();
        for (final PeerId peer : peers) {
            this.peers.add(peer.copy());
        }
    }
    public boolean addPeer(final PeerId peer) {
        return this.peers.add(peer);
    }

    public boolean removePeer(final PeerId peer) {
        return this.peers.remove(peer);
    }

    public boolean contains(final PeerId peer) {
        return this.peers.contains(peer);
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.learners == null) ? 0 : this.learners.hashCode());
        result = prime * result + ((this.peers == null) ? 0 : this.peers.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Configuration other = (Configuration) obj;
        if (this.learners == null) {
            if (other.learners != null) {
                return false;
            }
        } else if (!this.learners.equals(other.learners)) {
            return false;
        }
        if (this.peers == null) {
            return other.peers == null;
        } else {
            return this.peers.equals(other.peers);
        }
    }
    public List<PeerId> listPeers() {
        return new ArrayList<>(this.peers);
    }
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        final List<PeerId> peers = listPeers();
        int i = 0;
        int size = peers.size();
        for (final PeerId peer : peers) {
            sb.append(peer);
            if (i < size - 1 || !this.learners.isEmpty()) {
                sb.append(",");
            }
            i++;
        }
        size = this.learners.size();
        i = 0;
        for (final PeerId peer : this.learners) {
            sb.append(peer).append(LEARNER_POSTFIX);
            if (i < size - 1) {
                sb.append(",");
            }
            i++;
        }

        return sb.toString();
    }

}
