package com.alipay.sofa.jraft.core;

/**
 * @author : 小何
 * @Description : 节点选举的优先级
 * @date : 2024-04-04 17:55
 */
public class ElectionPriority {
    /**
     * 该节点禁用按照优先级选举的功能
     */
    public static final int Disable = -1;

    /**
     * 此节点不参与选举
     */
    public static final int NotElected = 0;
    /**
     * 如果此节点可以使用优先级选举，优先级最小值为1
     */
    public static final int MinValue = 1;
}
