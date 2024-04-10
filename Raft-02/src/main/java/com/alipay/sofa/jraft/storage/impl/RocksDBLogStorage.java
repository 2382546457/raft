package com.alipay.sofa.jraft.storage.impl;

import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.conf.ConfigurationEntry;
import com.alipay.sofa.jraft.conf.ConfigurationManager;
import com.alipay.sofa.jraft.entity.EnumOutter;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.entity.codec.LogEntryDecoder;
import com.alipay.sofa.jraft.entity.codec.LogEntryEncoder;
import com.alipay.sofa.jraft.option.LogStorageOptions;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.storage.LogStorage;
import com.alipay.sofa.jraft.util.*;
import org.apache.logging.log4j.core.tools.picocli.CommandLine;
import org.rocksdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-04 21:38
 */
public class RocksDBLogStorage implements LogStorage, Describer {
    private static final Logger logger = LoggerFactory.getLogger(RocksDBLogStorage.class);
    static {
        // 加载 RocksDB的 jar包
        RocksDB.loadLibrary();
    }
    private String groupId;
    /**
     * 日志的本地路径
     */
    private final String path;
    /**
     * 是否为同步刷盘
     */
    private final boolean sync;
    /**
     * RocksDB数据库对象，使用它操作数据库
     */
    private RocksDB db;
    /**
     * RocksDB 配置
     */
    private DBOptions dbOptions;
    /**
     * 写配置
     */
    private WriteOptions writeOptions;
    /**
     * 读配置
     */
    private ReadOptions totalOrderReadOptions;
    /**
     * 列族配置，每一个列族可以看作是一张表
     */
    private final List<ColumnFamilyOptions> cfOptions = new ArrayList<>();
    /**
     * 默认列族，操作日志表
     */
    private ColumnFamilyHandle defaultHandle;
    /**
     * 配置列族，操作配置表
     */
    private ColumnFamilyHandle confHandle;
    /**
     * 读写锁
     */
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();
    /**
     * 第一条日志的索引
     */
    private volatile long firstLogIndex = 1;
    /**
     * 是否已经加载了第一条日志
     */
    private volatile boolean hasLoadFirstLogIndex;
    /**
     * 日志编码器
     */
    private LogEntryEncoder logEntryEncoder;
    /**
     * 日志解码器
     */
    private LogEntryDecoder logEntryDecoder;

    public RocksDBLogStorage(String path, final RaftOptions raftOptions) {
        super();
        this.path = path;
        this.sync = raftOptions.isSync();
    }

    /**
     * 创建 RocksDB 数据库的配置项
     * @return
     */
    public static DBOptions createDBOptions() {
        return StorageOptionsFactory.getRocksDBOptions(RocksDBLogStorage.class);
    }

    /**
     * 创建列族的配置项
     * @return
     */
    public static ColumnFamilyOptions createColumnFamilyOptions() {
        final BlockBasedTableConfig tConfig = StorageOptionsFactory
                .getRocksDBTableFormatConfig(RocksDBLogStorage.class);
        return StorageOptionsFactory.getRocksDBColumnFamilyOptions(RocksDBLogStorage.class)
                .useFixedLengthPrefixExtractor(8)
                .setTableFormatConfig(tConfig)
                .setMergeOperator(new StringAppendOperator());
    }

    /**
     * 加载 RocksDB 数据库
     * @param opts
     * @return
     */
    @Override
    public boolean init(LogStorageOptions opts) {
        Requires.requireNonNull(opts.getConfigurationManager(), "Null conf manager");
        Requires.requireNonNull(opts.getLogEntryCodecFactory(), "Null log entry codec factory");
        this.groupId = opts.getGroupId();
        this.writeLock.lock();
        try {
            if (this.db != null) {
                logger.warn("RocksDBLogStorage init() in {} already.", this.path);
                return true;
            }
            // 从工厂中获取编码、解码器
            this.logEntryDecoder = opts.getLogEntryCodecFactory().decoder();
            this.logEntryEncoder = opts.getLogEntryCodecFactory().encoder();
            Requires.requireNonNull(this.logEntryDecoder, "Null log entry decoder");
            Requires.requireNonNull(this.logEntryEncoder, "Null log entry encoder");
            // 得到数据库的配置
            this.dbOptions = createDBOptions();
            // 得到写参数配置，设置写同步策略
            this.writeOptions = new WriteOptions();
            this.writeOptions.setSync(this.sync);
            // 得到读参数配置，指定顺序读
            this.totalOrderReadOptions = new ReadOptions();
            this.totalOrderReadOptions.setTotalOrderSeek(true);
            // 初始化数据库并加载 配置日志条目 到配置管理器中
            return initAndLoad(opts.getConfigurationManager());
        } catch (final RocksDBException e) {
            logger.error("Fail to init RocksDBLogStorage, path={}.", this.path, e);
            return false;
        } finally {
            this.writeLock.unlock();
        }
    }

    /**
     * 初始化数据库并加载 配置日志条目 到配置管理器中
     * @param configurationManager 配置管理器
     * @return
     */
    private boolean initAndLoad(ConfigurationManager configurationManager) throws RocksDBException {
        this.hasLoadFirstLogIndex = false;
        this.firstLogIndex = 1;
        final List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();
        final ColumnFamilyOptions cfOption = createColumnFamilyOptions();
        this.cfOptions.add(cfOption);
        // 给专门存放配置日志条目的列族创建一个描述符
        columnFamilyDescriptors.add(new ColumnFamilyDescriptor("Configuration".getBytes(), cfOption));
        // 给专门存放业务日志条目的列族创建一个描述符
        columnFamilyDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOption));
        // 启动数据库
        openDB(columnFamilyDescriptors);
        // 加载之前的配置日志到配置管理器中
        load(configurationManager);

        return onInitLoaded();
    }

    /**
     * 打开数据库的方法
     */
    private void openDB(final List<ColumnFamilyDescriptor> columnFamilyDescriptors) throws RocksDBException {
        // 这里定义的这个集合，是用来存放数据库返回给用户的，可以操作具体列族的句柄
        final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
        // 根据用户配置的路径，创建文件，向数据库中存放的数据都会存放到这个文件中
        // 我的数据最终存放在了 jraft-log/serverx/log 中
        final File dir = new File(this.path);
        //判断路径，校验是否为目录
        if (dir.exists() && !dir.isDirectory()) {
            throw new IllegalStateException("Invalid log path, it's a regular file: " + this.path);
        }
        // 打开数据库，返回操作数据库的句柄
        this.db = RocksDB.open(this.dbOptions, this.path, columnFamilyDescriptors, columnFamilyHandles);
        // 校验 columnFamilyHandles 是不是存放了两个列族句柄，因为我们定义的就是配置日志列族和业务日志列族
        assert (columnFamilyHandles.size() == 2);
        // 获得操纵配置日志列族的句柄
        this.confHandle = columnFamilyHandles.get(0);
        // 获得操纵业务日志列族的句柄
        this.defaultHandle = columnFamilyHandles.get(1);
    }

    public static final byte[] FIRST_LOG_IDX_KEY = Utils.getBytes("meta/firstLogIndex");
    private void checkState() {
        Requires.requireNonNull(this.db, "DB not initialized or destroyed");
    }

    private void load(final ConfigurationManager confManager) {
        // 校验数据库是否已经打开，就是判断成员变量db不为null
        checkState();
        try (final RocksIterator it = this.db.newIterator(this.confHandle, this.totalOrderReadOptions)) {
            //迭代器定位到第一个日志条目
            it.seekToFirst();
            //开始遍历
            while (it.isValid()) {
                //下面就是得到日志的键值对了
                //得到键，键就是日志的索引
                final byte[] ks = it.key();
                //得到value，value就是日志本身
                final byte[] bs = it.value();
                //判断键的长度，按照sofajraft中的设定，写入数据库的日志的键制度，其键的长度为8个字节
                //这一点可以在saveFirstLogIndex方法中查看
                if (ks.length == 8) {
                    //走到这里，说明当前遍历的就是一个日志条目，使用日志解码器解码
                    //得到日志条目对象
                    final LogEntry entry = this.logEntryDecoder.decode(bs);
                    //判空
                    if (entry != null) {
                        // 再校验一下日志条目对象的类型，看看是不是配置变更日志类型的
                        if (entry.getType() == EnumOutter.EntryType.ENTRY_TYPE_CONFIGURATION) {
                            // 如果是的话，接下来就要把日志信息填充到ConfigurationEntry对象中
                            final ConfigurationEntry confEntry = new ConfigurationEntry();
                            confEntry.setId(new LogId(entry.getId().getIndex(), entry.getId().getTerm()));
                            confEntry.setConf(new Configuration(entry.getPeers(), entry.getLearners()));
                            if (entry.getOldPeers() != null) {
                                confEntry.setOldConf(new Configuration(entry.getOldPeers(), entry.getOldLearners()));
                            }
                            // 再判断一下配置管理器是否不为空
                            if (confManager != null) {
                                // 如果不为空，就把配置日志条目添加到配置管理器中
                                confManager.add(confEntry);
                            }
                        }
                    } else {
                        //走到这里意味着解码失败
                        logger.warn("Fail to decode conf entry at index {}, the log data is: {}.", Bits.getLong(ks, 0),
                                BytesUtil.toHex(bs));
                    }
                } else {
                    if (Arrays.equals(FIRST_LOG_IDX_KEY, ks)) {
                        //得到具体的索引值，然后赋值给firstLogIndex成员变量
                        setFirstLogIndex(Bits.getLong(bs, 0));
                        //这里这个方法暂时还不用关注，因为还没有引入日志快照，加入有日志快照之后再剖析这个方法
                        //truncatePrefixInBackground(0L, this.firstLogIndex);
                    } else {
                        //走到这里说明是未知的数据
                        logger.warn("Unknown entry in configuration storage key={}, value={}.", BytesUtil.toHex(ks),
                                BytesUtil.toHex(bs));
                    }
                }
                //获取下一个日志条目，进入下一次循环
                it.next();
            }
        }
    }
    protected boolean onInitLoaded() {
        return true;
    }

    /**
     * 给第一条日志索引赋值
     * @param index
     */
    private void setFirstLogIndex(final long index) {
        this.firstLogIndex = index;
        this.hasLoadFirstLogIndex = true;
    }

    /**
     * 将第一条日志索引保存在配置列族中，以 key=meta/firstLogIndex, value=1 的形式
     * @param firstLogIndex
     * @return
     */
    private boolean saveFirstLogIndex(final long firstLogIndex) {
        this.readLock.lock();
        try {
            final byte[] vs = new byte[8];
            Bits.putLong(vs, 0, firstLogIndex);
            checkState();
            this.db.put(this.confHandle, this.writeOptions, FIRST_LOG_IDX_KEY, vs);
            return true;
        } catch (final RocksDBException e) {
            logger.error("Fail to save first log index {} in {}.", firstLogIndex, this.path, e);
            return false;
        } finally {
            this.readLock.unlock();
        }
    }
    @Override
    public long getFirstLogIndex() {
        this.readLock.lock();
        RocksIterator it = null;
        try {
            // 判断第一条日志索引有没有被加载，如果已经加载过了，就直接返回
            if (this.hasLoadFirstLogIndex) {
                return this.firstLogIndex;
            }
            // 检查数据库状态
            checkState();
            // 返回迭代器对象
            it = this.db.newIterator(this.defaultHandle, this.totalOrderReadOptions);
            // 定位第一条数据
            it.seekToFirst();
            // 判断第一条数据是否有效
            if (it.isValid()) {
                // 如果有效就得到第一条日志的索引
                final long ret = Bits.getLong(it.key(), 0);
                // 把第一条日志的索引存放到confHandle列族中
                saveFirstLogIndex(ret);
                // 给成员变量赋值，修改hasLoadFirstLogIndex状态
                setFirstLogIndex(ret);
                return ret;
            }
            // 如果数据无效就返回1
            return 1L;
        } finally {
            if (it != null) {
                it.close();
            }
            this.readLock.unlock();
        }
    }
    @Override
    public long getLastLogIndex() {
        this.readLock.lock();
        checkState();
        try (final RocksIterator it = this.db.newIterator(this.defaultHandle, this.totalOrderReadOptions)) {
            it.seekToLast();
            if (it.isValid()) {
                return Bits.getLong(it.key(), 0);
            }
            return 0L;
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public LogEntry getEntry(long index) {
        this.readLock.lock();
        try {
            // 判断索引是否合规，查询的索引必须大于第一条日志索引
            if (this.hasLoadFirstLogIndex && index < this.firstLogIndex) {
                return null;
            }
            return getEntryFromDB(index);
        } catch (final RocksDBException | IOException e) {
            logger.error("Fail to get log entry at index {} in data path: {}.", index, this.path, e);
        } finally {
            this.readLock.unlock();
        }
        return null;
    }

    private LogEntry getEntryFromDB(long index) throws RocksDBException, IOException {
        final byte[] keyBytes = getKeyBytes(index);
        byte[] bs = getValueFromRocksDB(keyBytes);
        if (bs != null) {
            final LogEntry entry = this.logEntryDecoder.decode(bs);
            if (entry != null) {
                return entry;
            } else {
                logger.error("Bad log entry format for index={}, the log data is: {}.", index, BytesUtil.toHex(bs));
                return null;
            }
        }
        return null;
    }
    protected byte[] getKeyBytes(final long index) {
        final byte[] ks = new byte[8];
        Bits.putLong(ks, 0, index);
        return ks;
    }

    /**
     * 根据 key 从 RocksDB 中拿到 value
     * @param keyBytes
     * @return
     */
    protected byte[] getValueFromRocksDB(final byte[] keyBytes) throws RocksDBException {
        checkState();
        return this.db.get(this.defaultHandle, keyBytes);
    }

    @Override
    public long getTerm(final long index) {
        final LogEntry entry = getEntry(index);
        if (entry != null) {
            return entry.getId().getTerm();
        }
        return 0;
    }
    @Override
    public void shutdown() {
        //暂时不做实现
    }
    @Override
    public void describe(final Printer out) {
        this.readLock.lock();
        try {
            if (this.db != null) {
                out.println(this.db.getProperty("rocksdb.stats"));
            }
            out.println("");
        } catch (final RocksDBException e) {
            out.println(e);
        } finally {
            this.readLock.unlock();
        }
    }
    private void closeDB() {
        this.confHandle.close();
        this.defaultHandle.close();
        this.db.close();
    }

}
