package com.alipay.sofa.jraft.conf;

import com.alipay.sofa.jraft.util.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * @author : 小何
 * @Description : 配置管理器
 * 管理着当前节点中所有关于配置信息的条目，集群中每一次配置的变更都会产生日志，也就是 ConfigurationEntry，集群的配置变成就会存进去然后放到此处
 * @date : 2024-04-04 23:19
 */
public class ConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);
    /**
     * 配置变更信息
     */
    private final LinkedList<ConfigurationEntry> configurations = new LinkedList<>();
    /**
     * 节点当前有效的配置信息
     */
    private ConfigurationEntry snapshot = new ConfigurationEntry();

    public boolean add(final ConfigurationEntry entry) {
        if (!this.configurations.isEmpty()) {
            //这里是添加的日志条目的索引进行判断，先得到链表中最新的配置条目索引，再和传进来的做比较
            //不可能我当前变更的配置条目索引小于上一次的配置信息索引
            if (this.configurations.peekLast().getId().getIndex() >= entry.getId().getIndex()) {
                logger.error("Did you forget to call truncateSuffix before the last log index goes back.");
                return false;
            }
        }
        return this.configurations.add(entry);
    }
    public void truncatePrefix(final long firstIndexKept) {
        while (!this.configurations.isEmpty() && this.configurations.peekFirst().getId().getIndex() < firstIndexKept) {
            this.configurations.pollFirst();
        }
    }
    //从链表中移除索引比firstIndexKept大的的所有配置条目
    public void truncateSuffix(final long lastIndexKept) {
        while (!this.configurations.isEmpty() && this.configurations.peekLast().getId().getIndex() > lastIndexKept) {
            this.configurations.pollLast();
        }
    }
    //得到节点最新的配置信息
    public ConfigurationEntry getSnapshot() {
        return this.snapshot;
    }

    public void setSnapshot(final ConfigurationEntry snapshot) {
        this.snapshot = snapshot;
    }

    // 这个方法也是用来获得节点最新的配置条目
    public ConfigurationEntry getLastConfiguration() {
        if (this.configurations.isEmpty()) {
            return snapshot;
        } else {
            return this.configurations.peekLast();
        }
    }
    public ConfigurationEntry get(final long lastIncludedIndex) {
        //判空
        if (this.configurations.isEmpty()) {
            Requires.requireTrue(lastIncludedIndex >= this.snapshot.getId().getIndex(),
                    "lastIncludedIndex %d is less than snapshot index %d", lastIncludedIndex, this.snapshot.getId()
                            .getIndex());
            return this.snapshot;
        }
        ListIterator<ConfigurationEntry> it = this.configurations.listIterator();
        while (it.hasNext()) {
            if (it.next().getId().getIndex() > lastIncludedIndex) {
                //这里就是发现得到的数据索引大于lastIncludedIndex，所以回退一下
                it.previous();
                break;
            }
        }
        //回退完毕的配置条目索引是等于lastIncludedIndex，所以要判断是否有前一个数据
        if (it.hasPrevious()) {
            //有的话则返回，返回的就是小于lastIncludedIndex索引的最大配置条目
            return it.previous();
        } else {
            return this.snapshot;
        }
    }
}
