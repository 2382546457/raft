package com.alipay.sofa.jraft.core;

/**
 * 复制器类型，分为普通跟随者和学习者
 */
public enum ReplicatorType {
    Follower, Learner;

    public final boolean isFollower() {
        return this == Follower;
    }

    public final boolean isLearner() {
        return this == Learner;
    }
}