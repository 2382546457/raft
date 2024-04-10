package com.alipay.sofa.jraft.storage;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Lifecycle;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.ConfigurationEntry;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.option.LogManagerOptions;
import com.alipay.sofa.jraft.util.Describer;

import java.util.List;

public interface LogManager extends Lifecycle<LogManagerOptions>, Describer {


    abstract class StableClosure implements Closure {

        protected long           firstLogIndex = 0;
        protected List<LogEntry> entries;
        protected int            nEntries;

        public StableClosure() {
            // NO-OP
        }

        public long getFirstLogIndex() {
            return this.firstLogIndex;
        }

        public void setFirstLogIndex(final long firstLogIndex) {
            this.firstLogIndex = firstLogIndex;
        }

        public List<LogEntry> getEntries() {
            return this.entries;
        }

        public void setEntries(final List<LogEntry> entries) {
            this.entries = entries;
            if (entries != null) {
                this.nEntries = entries.size();
            } else {
                this.nEntries = 0;
            }
        }

        public StableClosure(final List<LogEntry> entries) {
            super();
            setEntries(entries);
        }

    }

    //该接口中的方法在第四版本代码中将会用到，现在写在这里是为后面迭代代码省一点功夫。。
    interface LastLogIndexListener {

        void onLastLogIndexChanged(final long lastLogIndex);
    }


    //将一批日志持久化的方法
    void appendEntries(final List<LogEntry> entries, StableClosure done);



    //根据索引获得指定日志条目
    LogEntry getEntry(final long index);

    //根据日志索引获得对应任期的方法
    long getTerm(final long index);

    //得到第一条日志索引的方法
    long getFirstLogIndex();

    //得到最后一条日志索引的方法
    long getLastLogIndex();

    //得到最后一条日志的索引，这个方法和上面方法的区别是
    //当调用这个方法的时候，如果方法参数isFlush为true
    //就会讲内存中的日志刷新到硬盘中，然后返回最新的最后一条日志的索引
    //这个方法会在第三版本得到真正实现
    long getLastLogIndex(final boolean isFlush);

    //得到最后一条日志ID
    LogId getLastLogId(final boolean isFlush);

    //根据索引得到指定的配置日志条目
    ConfigurationEntry getConfiguration(final long index);

    //检查是否需要更新配置信息
    ConfigurationEntry checkAndSetConfiguration(final ConfigurationEntry current);

    //下面这个接口会在第四版本用到，并且发挥关键作用
    interface NewLogCallback {


        boolean onNewLog(final Object arg, final int errorCode);
    }

    //检查日志索引一致性，这里的检查所以一致性其实没那么复杂
    //当引入快照机制之后，每一次生成快照，都会把已生成快照的日志删除，所以
    //每次程序启动的时候，都会判断快照中的最后一条日志的索引和数据库中的第一条日志的索引是否
    //无缝衔接，如果中间有空隙，比如快照的最后一条索引为23，数据库的第一条索引为29
    //这显然就对不上了，数据肯定有丢失
    Status checkConsistency();
}
