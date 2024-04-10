package com.alipay.sofa.jraft.entity;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.util.Requires;

import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * 业务层提交的数据
 */
public class Task implements Serializable {

    private static final long serialVersionUID = 2971309899898274575L;

    /**
     * 业务层提交的指令
     */
    private ByteBuffer data = LogEntry.EMPTY_DATA;
    /**
     * 当业务层的指令被集群处理之后做的回调，一般在这里通知业务层指令是否持久化成功
     */
    private Closure done;

    private long expectedTerm = -1;

    public Task() {
        super();
    }


    public Task(final ByteBuffer data, final Closure done) {
        super();
        this.data = data;
        this.done = done;
    }


    public Task(final ByteBuffer data, final Closure done, final long expectedTerm) {
        super();
        this.data = data;
        this.done = done;
        this.expectedTerm = expectedTerm;
    }

    public ByteBuffer getData() {
        return this.data;
    }

    public void setData(final ByteBuffer data) {
        Requires.requireNonNull(data, "data should not be null, you can use LogEntry.EMPTY_DATA instead.");
        this.data = data;
    }

    public Closure getDone() {
        return this.done;
    }

    public void setDone(final Closure done) {
        this.done = done;
    }

    public long getExpectedTerm() {
        return this.expectedTerm;
    }

    public void setExpectedTerm(final long expectedTerm) {
        this.expectedTerm = expectedTerm;
    }

}