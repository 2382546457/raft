package com.alipay.sofa.jraft.conf;

import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.entity.PeerId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * @author : 小何
 * @Description : 配置日志条目，需要存储在RocksDB中
 * @date : 2024-04-04 22:06
 */
public class ConfigurationEntry {
    private static final Logger LOG     = LoggerFactory.getLogger(ConfigurationEntry.class);
    /**
     * ConfigurationEntry本质上也是一条日志，所以需要日志ID
     */
    private LogId id = new LogId(0, 0);
    /**
     * 当前生效的配置
     */
    private Configuration conf = new Configuration();
    /**
     * 旧配置
     */
    private Configuration oldConf = new Configuration();

    /**
     *
     * @return
     */
    public boolean isValid() {
        if (!this.conf.isValid()) {
            return false;
        }
        final Set<PeerId> intersection = listPeers();
        intersection.retainAll(listLearners());
        if (intersection.isEmpty()) {
            return true;
        }
        LOG.error("Invalid conf entry {}, peers and learners have intersection: {}.", this, intersection);
        return false;
    }


    /**
     * 把当前配置和旧配置中的所有Learner以集合的形式返回
     * @return
     */
    public Set<PeerId> listLearners() {
        final Set<PeerId> ret = new HashSet<>(this.conf.getLearners());
        ret.addAll(this.oldConf.getLearners());
        return ret;
    }
    /**
     * 判断旧的配置是否为空，如果旧配置为空，说明当前集群的配置没有进行过变更，也就代表当前集群是很稳定的
     * @return
     */
    public boolean isStable() {
        return this.oldConf.isEmpty();
    }

    /**
     * 判断当前配置是否为空
     * @return
     */
    public boolean isEmpty() {
        return this.conf.isEmpty();
    }

    /**
     * 把当前配置和就配置中的所有PeerId以集合的形式返回
     * @return
     */
    public Set<PeerId> listPeers() {
        final Set<PeerId> ret = new HashSet<>(this.conf.listPeers());
        ret.addAll(this.oldConf.listPeers());
        return ret;
    }

    /**
     * 判断某个learner是否存在于当前配置或旧配置中
     * @param learner
     * @return
     */
    public boolean containsLearner(final PeerId learner) {
        return this.conf.getLearners().contains(learner) || this.oldConf.getLearners().contains(learner);
    }
    public LogId getId() {
        return this.id;
    }


    public void setId(final LogId id) {
        this.id = id;
    }


    /**
     * 判断某个PeerId是否存在于当前配置或旧配置中
     * @param peer
     * @return
     */
    public boolean contains(final PeerId peer) {
        return this.conf.contains(peer) || this.oldConf.contains(peer);
    }


    @Override
    public String toString() {
        return "ConfigurationEntry [id=" + this.id + ", conf=" + this.conf + ", oldConf=" + this.oldConf + "]";
    }
    public Configuration getConf() {
        return this.conf;
    }


    public void setConf(final Configuration conf) {
        this.conf = conf;
    }


    public Configuration getOldConf() {
        return this.oldConf;
    }


    public void setOldConf(final Configuration oldConf) {
        this.oldConf = oldConf;
    }


    public ConfigurationEntry() {
        super();
    }


}
