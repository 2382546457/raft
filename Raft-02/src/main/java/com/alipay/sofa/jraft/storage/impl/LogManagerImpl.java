package com.alipay.sofa.jraft.storage.impl;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.ConfigurationEntry;
import com.alipay.sofa.jraft.conf.ConfigurationManager;
import com.alipay.sofa.jraft.core.NodeMetrics;
import com.alipay.sofa.jraft.entity.EnumOutter;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.LogId;

import com.alipay.sofa.jraft.error.LogEntryCorruptedException;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.option.LogManagerOptions;
import com.alipay.sofa.jraft.option.LogStorageOptions;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.storage.LogManager;
import com.alipay.sofa.jraft.storage.LogStorage;
import com.alipay.sofa.jraft.util.Requires;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.TimeoutBlockingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
public class LogManagerImpl implements LogManager {

    private static final Logger LOG = LoggerFactory.getLogger(LogManagerImpl.class);

    private String groupId;
    //日志存储器，这个类虽然持有日志管理器，但是并不能意味着
    //这个类就是对日志管理器做了层代理
    //实际上，这个类做了很多其他的工作，为了提高日志落盘的性能等等
    //实际上，这个类中还有一个disruptor队列，下一版本大家就会见到了
    private LogStorage logStorage;
    private ConfigurationManager configManager;
    //读写锁
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock writeLock = this.lock.writeLock();
    private final Lock readLock = this.lock.readLock();
    //日志管理器是否停止工作
    private volatile boolean stopped;
    private volatile boolean hasError;
    //这里指的是存储到硬盘上的最后一条日志的ID
    private LogId diskId = new LogId(0, 0);
    //最新的被应用到状态机的日志ID
    private LogId appliedId = new LogId(0, 0);
    //第一条日志的索引，默认从1开始
    private volatile long firstLogIndex;
    //最后一条日志的索引，这两个成员变量在checkConsistency方法中会用到
    //也就是检查日志索引一致性的时候，lastLogIndex默认为0
    private volatile long lastLogIndex;
    private RaftOptions raftOptions;
    private NodeMetrics nodeMetrics;


    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/12/1
     * @Description:初始化方法，这个方法的逻辑比较简单，所以注释也添加的简单一些
     */
    @Override
    public boolean init(final LogManagerOptions opts) {
        this.writeLock.lock();
        try {
            if (opts.getLogStorage() == null) {
                LOG.error("Fail to init log manager, log storage is null");
                return false;
            }
            this.groupId = opts.getGroupId();
            this.raftOptions = opts.getRaftOptions();
            this.nodeMetrics = opts.getNodeMetrics();
            this.logStorage = opts.getLogStorage();
            this.configManager = opts.getConfigurationManager();
            //在这里创建一个封装日志存储器需要的参数对象
            LogStorageOptions lsOpts = new LogStorageOptions();
            lsOpts.setGroupId(opts.getGroupId());
            //设置配置管理器
            lsOpts.setConfigurationManager(this.configManager);
            //设置编解码工厂，这个编解码工厂会交给日志管理器使用，当日志管理器将日志落盘
            //或者是从数据库读取日志时，就会用到这个编解码工厂提供的编解码器进行编码和解码
            lsOpts.setLogEntryCodecFactory(opts.getLogEntryCodecFactory());
            //初始化日志存储器
            if (!this.logStorage.init(lsOpts)) {
                //初始化失败则报错
                LOG.error("Fail to init logStorage");
                return false;
            }//下面就是简单的赋值操作了
            this.firstLogIndex = this.logStorage.getFirstLogIndex();
            this.lastLogIndex = this.logStorage.getLastLogIndex();
            //从硬盘中得到最后一条日志的ID，getTermFromLogStorage方法是根据日志的索引得到相对应的任期
            this.diskId = new LogId(this.lastLogIndex, getTermFromLogStorage(this.lastLogIndex));
        } finally {
            this.writeLock.unlock();
        }
        return true;
    }

    @Override
    public void shutdown() {
        //暂且不做实现
    }


    //将一批日志持久化的方法
    @Override
    public void appendEntries(final List<LogEntry> entries, final StableClosure done) {
        //暂时不做实现，第三版本再实现
    }



    private void reportError(final int code, final String fmt, final Object... args) {
        this.hasError = true;
        final RaftException error = new RaftException(EnumOutter.ErrorType.ERROR_TYPE_LOG);
        error.setStatus(new Status(code, fmt, args));
    }


    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/11/30
     * @Description:根据索引获得日志条目的方法，该方法省略了先从内存中获得日志条目的步骤
     */
    @Override
    public LogEntry getEntry(long index) {
        //这里使用的是读锁，可以同时读，但不能修改
        this.readLock.lock();
        try {//检验日志索引是否合规，日志索引不能比最后一条索引大，不能比第一条索引小
            if (index > this.lastLogIndex || index < this.firstLogIndex) {
                return null;
            }
        } finally {
            this.readLock.unlock();
        }//从日志存储器中获得指定索引的日志条目
        final LogEntry entry = this.logStorage.getEntry(index);
        if (entry == null) {
            //如果日志条目为空则报错
            reportError(RaftError.EIO.getNumber(), "Corrupted entry at index=%d, not found", index);
        }
        //这里判断了一下是否开启了校验和功能，如果开启了，就将从数据存储器中获得的日志的校验和之前记录的做一下对比
        //如果不想等则意味着日志有损坏
        if (entry != null && this.raftOptions.isEnableLogEntryChecksum() && entry.isCorrupted()) {
            String msg = String.format("Corrupted entry at index=%d, term=%d, expectedChecksum=%d, realChecksum=%d",
                    index, entry.getId().getTerm(), entry.getChecksum(), entry.checkSum());
            reportError(RaftError.EIO.getNumber(), msg);
            throw new LogEntryCorruptedException(msg);
        }
        return entry;
    }

    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/11/30
     * @Description:该方法是根据索引获得日志的任期，这个方法中省略了两个步骤
     * 完整的逻辑是首先会获得日志快照的最后一次Id的索引，然后进行判断，如果没有成功
     * 就直接从内存中获得对应日志的任期，因为最新的日志都会存放在内存中
     * 如果获取不成功，最后才会从日志存储器中获得对应日志的任期
     */
    @Override
    public long getTerm(long index) {
        if (index == 0) {
            return 0;
        }
        if (index > this.lastLogIndex || index < this.firstLogIndex) {
            return 0;
        }//该方法就是直接从日志存储器中获得指定索引日志的任期
        return getTermFromLogStorage(index);
    }


    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/12/1
     * @Description:从日志存储器中获得指定索引日志的任期
     */
    private long getTermFromLogStorage(final long index) {
        //从日志存储器中获得指定索引对应的日志条目
        final LogEntry entry = this.logStorage.getEntry(index);
        if (entry != null) {
            //通过对校验和来判断日志是否有存坏
            if (this.raftOptions.isEnableLogEntryChecksum() && entry.isCorrupted()) {
                final String msg = String.format(
                        "The log entry is corrupted, index=%d, term=%d, expectedChecksum=%d, realChecksum=%d", entry
                                .getId().getIndex(), entry.getId().getTerm(), entry.getChecksum(), entry.checkSum());
                reportError(RaftError.EIO.getNumber(), msg);
                throw new LogEntryCorruptedException(msg);
            }
            //返回日志任期
            return entry.getId().getTerm();
        }
        return 0;
    }

    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/12/1
     * @Description:得到第一条日志索引，下面这几个方法多多少少都有删减，下一版本将会重构完整
     */
    @Override
    public long getFirstLogIndex() {
        this.readLock.lock();
        try {
            return this.firstLogIndex;
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public long getLastLogIndex() {
        return getLastLogIndex(false);
    }

    @Override
    public long getLastLogIndex(boolean isFlush) {
        this.readLock.lock();
        try {
            return this.lastLogIndex;

        } finally {
            this.readLock.unlock();
        }
    }


    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/11/30
     * @Description:该方法也是根据索引获得日志的任期，这个方法中省略了两个步骤
     * 完整的逻辑是首先会获得日志快照的最后一次Id的索引，然后进行判断，就是和方法参数中的索引对比看是否相等，如果不想等
     * 就直接从内存中获得对应日志的任期，因为最新的日志都会存放在内存中
     * 如果获取不成功，最后才会从日志存储器中获得对应日志的任期
     */
    private long unsafeGetTerm(final long index) {
        if (index == 0) {
            return 0;
        }
        if (index > this.lastLogIndex || index < this.firstLogIndex) {
            return 0;
        }
        return getTermFromLogStorage(index);
    }

    @Override
    public LogId getLastLogId(final boolean isFlush) {
        this.readLock.lock();
        try {

            return new LogId(this.lastLogIndex, unsafeGetTerm(this.lastLogIndex));
        } finally {
            this.readLock.unlock();
        }
    }

    //根据索引得到指定的配置日志条目
    @Override
    public ConfigurationEntry getConfiguration(final long index) {
        this.readLock.lock();
        try {
            return this.configManager.get(index);
        } finally {
            this.readLock.unlock();
        }
    }


    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/11/30
     * @Description:把最新的配置条目信息返回出去
     */
    @Override
    public ConfigurationEntry checkAndSetConfiguration(final ConfigurationEntry current) {
        if (current == null) {
            return null;
        }
        this.readLock.lock();
        try {
            final ConfigurationEntry lastConf = this.configManager.getLastConfiguration();
            if (lastConf != null && !lastConf.isEmpty() && !current.getId().equals(lastConf.getId())) {
                return lastConf;
            }
        } finally {
            this.readLock.unlock();
        }
        return current;
    }


    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/12/1
     * @Description:校验日志索引一致性的方法
     */
    @Override
    public Status checkConsistency() {
        this.readLock.lock();
        try {
            //对第一条日志索引和最后一条日志索引校验
            Requires.requireTrue(this.firstLogIndex > 0);
            Requires.requireTrue(this.lastLogIndex >= 0);
            //这里是因为firstLogIndex初始值默认就为1，而且在第二版本中默认集群中没有日志产生，所以就判断firstLogIndex是否仍未初始值即可
            //下一个版本我会为大家实现日志落盘，会提供专门的测试类来进行日志落盘功能的测试
            if (this.firstLogIndex == 1) {
                return Status.OK();
            }
            return new Status(RaftError.EIO, "Missing logs in (0, %d)", this.firstLogIndex);
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public void describe(final Printer out) {
        final long _firstLogIndex;
        final long _lastLogIndex;
        final String _diskId;
        final String _appliedId;
        final String _lastSnapshotId;
        this.readLock.lock();
        try {
            _firstLogIndex = this.firstLogIndex;
            _lastLogIndex = this.lastLogIndex;
            _diskId = String.valueOf(this.diskId);
            _appliedId = String.valueOf(this.appliedId);
        } finally {
            this.readLock.unlock();
        }
        out.print("  storage: [")
                .print(_firstLogIndex)
                .print(", ")
                .print(_lastLogIndex)
                .println(']');
        out.print("  diskId: ")
                .println(_diskId);
        out.print("  appliedId: ")
                .println(_appliedId);
    }


}