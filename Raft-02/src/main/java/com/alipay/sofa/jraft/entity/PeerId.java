package com.alipay.sofa.jraft.entity;

import com.alipay.sofa.jraft.core.ElectionPriority;
import com.alipay.sofa.jraft.util.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * @author : 小何
 * @Description : 代表一个 Raft 节点
 * @date : 2024-04-04 18:07
 */
public class PeerId implements Copiable<PeerId>, Serializable, Checksum {
    private static final long serialVersionUID = 8083529734784884641L;

    private static final Logger logger = LoggerFactory.getLogger(PeerId.class);

    private Endpoint endpoint = new Endpoint(Utils.IP_ANY, 0);
    /**
     * 同样的IP地址下，用来区分集群中不同节点的
     */
    private int idx;
    /**
     * 对 toString() 结果的缓存
     */
    private String str;
    /**
     * 默认禁用选举优先级
     */
    private int priority = ElectionPriority.Disable;
    public static final PeerId ANY_PEER = new PeerId();
    private long checksum;

    public PeerId() {
        super();
    }

    /**
     * 计算当前 PeerId 的校验和
     */
    @Override
    public long checkSum() {
        if (this.checksum == 0) {
            this.checksum = CrcUtil.crc64(AsciiStringUtil.unsafeEncode(toString()));
        }
        return this.checksum;
    }

    @Override
    public PeerId copy() {
        return new PeerId(this.endpoint.copy(), this.idx, this.priority);
    }
    //根据给定的字符串解析并创建一个PeerId对象
    public static PeerId parsePeer(final String s) {
        final PeerId peer = new PeerId();
        if (peer.parse(s)) {
            return peer;
        }
        return null;
    }

    public PeerId(final Endpoint endpoint, final int idx) {
        super();
        this.endpoint = endpoint;
        this.idx = idx;
    }

    public PeerId(final String ip, final int port) {
        this(ip, port, 0);
    }

    public PeerId(final String ip, final int port, final int idx) {
        super();
        this.endpoint = new Endpoint(ip, port);
        this.idx = idx;
    }

    public PeerId(final Endpoint endpoint, final int idx, final int priority) {
        super();
        this.endpoint = endpoint;
        this.idx = idx;
        this.priority = priority;
    }

    public PeerId(final String ip, final int port, final int idx, final int priority) {
        super();
        this.endpoint = new Endpoint(ip, port);
        this.idx = idx;
        this.priority = priority;
    }

    /**
     * 创建一个空节点对象
     */
    public static PeerId emptyPeer() {
        return new PeerId();
    }

    /**
     * 当前节点信息对象是否为空
     * @return
     */
    public boolean isEmpty() {
        return getIp().equals(Utils.IP_ANY) && getPort() == 0 && this.idx == 0;
    }
    public String getIp() {
        return this.endpoint.getIp();
    }

    public int getPort() {
        return this.endpoint.getPort();
    }
    public Endpoint getEndpoint() {
        return this.endpoint;
    }

    public int getIdx() {
        return this.idx;
    }

    public int getPriority() {
        return priority;
    }

    //设置当前节点选举优先级
    public void setPriority(int priority) {
        this.priority = priority;
        this.str = null;
    }

    /**
     * 根据传入的字符串解析为一个 PeerId，字符串格式为 IP地址，端口号，idx，优先级
     *
     * @param str
     */
    public boolean parse(String str) {
        if (StringUtils.isEmpty(str)) {
            return false;
        }
        String[] tmps = Utils.parsePeerId(str);
        // 最少要有IP+PORT
        if (tmps.length < 2 || tmps.length > 4) {
            return false;
        }
        try {
            //把端口号转化为int
            final int port = Integer.parseInt(tmps[1]);
            //创建Endpoint对象封装IP地址和端口号信息
            this.endpoint = new Endpoint(tmps[0], port);
            //开始判断数组的长度了
            switch (tmps.length) {
                case 3:
                    //如果数组长度为3，说明用户配置了idx，给idx赋值即可
                    this.idx = Integer.parseInt(tmps[2]);
                    break;
                case 4:
                    //为4则说明优先级和idx都被配置了，先判断了一下idx是否为空
                    if (tmps[2].equals("")) {
                        //为空则赋值为0
                        this.idx = 0;
                    } else {
                        //不为空则直接给idx赋值
                        this.idx = Integer.parseInt(tmps[2]);
                    }
                    //给优先级赋值
                    this.priority = Integer.parseInt(tmps[3]);
                    break;
                default:
                    break;
            }
            this.str = null;
            return true;
        } catch (final Exception e) {
            logger.error("Parse peer from string failed: {}.", str, e);
            return false;
        }
    }

    /**
     * 判断当前节点是否会不会参加选举
     */
    public boolean isPriorityNotElected() {
        return this.priority == ElectionPriority.NotElected;
    }

    public boolean isPriorityDisabled() {
        return this.priority <= ElectionPriority.Disable;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.endpoint == null ? 0 : this.endpoint.hashCode());
        result = prime * result + this.idx;
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
        final PeerId other = (PeerId) obj;
        if (this.endpoint == null) {
            if (other.endpoint != null) {
                return false;
            }
        } else if (!this.endpoint.equals(other.endpoint)) {
            return false;
        }
        return this.idx == other.idx;
    }
    @Override
    public String toString() {
        if (this.str == null) {
            final StringBuilder buf = new StringBuilder(this.endpoint.toString());

            if (this.idx != 0) {
                buf.append(':').append(this.idx);
            }

            if (this.priority != ElectionPriority.Disable) {
                if (this.idx == 0) {
                    buf.append(':');
                }
                buf.append(':').append(this.priority);
            }

            this.str = buf.toString();
        }
        return this.str;
    }

}
