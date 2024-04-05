package com.alipay.sofa.jraft.core;

/**
 * @author : 小何
 * @Description : 节点状态枚举类
 * @date : 2024-04-04 17:58
 */
public enum State {
    /**
     * 该节点为领导者
     */
    STATE_LEADER,
    /**
     * 该节点正在移交领导权
     */
    STATE_TRANSFERRING,
    /**
     * 该节点为候选者，正在参与选举
     */
    STATE_CANDIDATE,
    /**
     * 该节点为跟随者
     */
    STATE_FOLLOWER,

    /**
     * 该节点出现了错误
     */
    STATE_ERROR,

    /**
     * 该节点还未初始化
     */
    STATE_UNINITIALIZED,
    /**
     * 该节点正在关闭
     */
    STATE_SHUTTING,

    /**
     * 已经停止工作
     */
    STATE_SHUTDOWN;

    /**
     * 判断该节点是否处于活跃状态
     * @return
     */
    public boolean isActive() {
        return this.ordinal() < STATE_ERROR.ordinal();
    }
}
