package com.alipay.sofa.jraft.option;

import com.alipay.sofa.jraft.util.Copiable;
import com.alipay.sofa.jraft.util.RpcFactoryHelper;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-04-04 19:35
 */
public class RaftOptions implements Copiable<RaftOptions> {
    /**
     * 每一个RPC请求最大字节数
     */
    private int maxByteCountPerRpc = 128 * 1024;

    private boolean fileCheckHole = false;

    /**
     * 最大条目数量
     */
    private int maxEntriesSize = 1024;

    private int maxBodySize = 512 * 1024;

    private int maxAppendBufferSize;

    /**
     * 进行选举时的最大延迟时间，默认1s
     */
    private int maxElectionDelayMs = 1000;

    private int electionHeartbeatFactor = 10;

    private int applyBatch = 32;

    private boolean sync = true;

    private boolean syncMeta = false;

    private boolean openStatistics = true;

    /**
     * 命令批处理
     */
    private boolean replicatorPipeline = true;

    private int maxReplicatorInflightMsgs = 256;
    /**
     * Disruptor数组的大小
     */
    private int disruptorBufferSize = 16384;

    private int disruptorPublishEventWaitTimeoutSecs = 10;

    /**
     * 是否开启 Entry条目 校验和
     */
    private boolean enableLogEntryChecksum = false;

    private int maxReadIndexLag = -1;

    /**
     * 选举超时之后，当前候选者节点是否变成跟随者
     */
    private boolean stepDownWhenVoteTimedout = true;

    private boolean startupOldStorage = false;


    public boolean isStepDownWhenVoteTimedout() {
        return this.stepDownWhenVoteTimedout;
    }

    public void setStepDownWhenVoteTimedout(final boolean stepDownWhenVoteTimeout) {
        this.stepDownWhenVoteTimedout = stepDownWhenVoteTimeout;
    }

    public int getDisruptorPublishEventWaitTimeoutSecs() {
        return this.disruptorPublishEventWaitTimeoutSecs;
    }

    public void setDisruptorPublishEventWaitTimeoutSecs(final int disruptorPublishEventWaitTimeoutSecs) {
        this.disruptorPublishEventWaitTimeoutSecs = disruptorPublishEventWaitTimeoutSecs;
    }

    public boolean isEnableLogEntryChecksum() {
        return this.enableLogEntryChecksum;
    }

    public void setEnableLogEntryChecksum(final boolean enableLogEntryChecksumValidation) {
        this.enableLogEntryChecksum = enableLogEntryChecksumValidation;
    }

    public int getMaxReadIndexLag() {
        return maxReadIndexLag;
    }

    public void setMaxReadIndexLag(int maxReadIndexLag) {
        this.maxReadIndexLag = maxReadIndexLag;
    }

    public boolean isReplicatorPipeline() {
        return this.replicatorPipeline && RpcFactoryHelper.rpcFactory().isReplicatorPipelineEnabled();
    }

    public void setReplicatorPipeline(final boolean replicatorPipeline) {
        this.replicatorPipeline = replicatorPipeline;
    }

    public int getMaxReplicatorInflightMsgs() {
        return this.maxReplicatorInflightMsgs;
    }

    public void setMaxReplicatorInflightMsgs(final int maxReplicatorPiplelinePendingResponses) {
        this.maxReplicatorInflightMsgs = maxReplicatorPiplelinePendingResponses;
    }

    public int getDisruptorBufferSize() {
        return this.disruptorBufferSize;
    }

    public void setDisruptorBufferSize(final int disruptorBufferSize) {
        this.disruptorBufferSize = disruptorBufferSize;
    }

    public int getMaxByteCountPerRpc() {
        return this.maxByteCountPerRpc;
    }

    public void setMaxByteCountPerRpc(final int maxByteCountPerRpc) {
        this.maxByteCountPerRpc = maxByteCountPerRpc;
    }

    public boolean isFileCheckHole() {
        return this.fileCheckHole;
    }

    public void setFileCheckHole(final boolean fileCheckHole) {
        this.fileCheckHole = fileCheckHole;
    }

    public int getMaxEntriesSize() {
        return this.maxEntriesSize;
    }

    public void setMaxEntriesSize(final int maxEntriesSize) {
        this.maxEntriesSize = maxEntriesSize;
    }

    public int getMaxBodySize() {
        return this.maxBodySize;
    }

    public void setMaxBodySize(final int maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    public int getMaxAppendBufferSize() {
        return this.maxAppendBufferSize;
    }

    public void setMaxAppendBufferSize(final int maxAppendBufferSize) {
        this.maxAppendBufferSize = maxAppendBufferSize;
    }

    public int getMaxElectionDelayMs() {
        return this.maxElectionDelayMs;
    }

    public void setMaxElectionDelayMs(final int maxElectionDelayMs) {
        this.maxElectionDelayMs = maxElectionDelayMs;
    }

    public int getElectionHeartbeatFactor() {
        return this.electionHeartbeatFactor;
    }

    public void setElectionHeartbeatFactor(final int electionHeartbeatFactor) {
        this.electionHeartbeatFactor = electionHeartbeatFactor;
    }

    public int getApplyBatch() {
        return this.applyBatch;
    }

    public void setApplyBatch(final int applyBatch) {
        this.applyBatch = applyBatch;
    }

    public boolean isSync() {
        return this.sync;
    }

    public void setSync(final boolean sync) {
        this.sync = sync;
    }

    public boolean isSyncMeta() {
        return this.sync || this.syncMeta;
    }

    public void setSyncMeta(final boolean syncMeta) {
        this.syncMeta = syncMeta;
    }

    public boolean isOpenStatistics() {
        return this.openStatistics;
    }

    public void setOpenStatistics(final boolean openStatistics) {
        this.openStatistics = openStatistics;
    }

    public boolean isStartupOldStorage() {
        return startupOldStorage;
    }

    public void setStartupOldStorage(final boolean startupOldStorage) {
        this.startupOldStorage = startupOldStorage;
    }


    @Override
    public RaftOptions copy() {
        final RaftOptions raftOptions = new RaftOptions();
        raftOptions.setMaxByteCountPerRpc(this.maxByteCountPerRpc);
        raftOptions.setFileCheckHole(this.fileCheckHole);
        raftOptions.setMaxEntriesSize(this.maxEntriesSize);
        raftOptions.setMaxBodySize(this.maxBodySize);
        raftOptions.setMaxAppendBufferSize(this.maxAppendBufferSize);
        raftOptions.setMaxElectionDelayMs(this.maxElectionDelayMs);
        raftOptions.setElectionHeartbeatFactor(this.electionHeartbeatFactor);
        raftOptions.setApplyBatch(this.applyBatch);
        raftOptions.setSync(this.sync);
        raftOptions.setSyncMeta(this.syncMeta);
        raftOptions.setOpenStatistics(this.openStatistics);
        raftOptions.setReplicatorPipeline(this.replicatorPipeline);
        raftOptions.setMaxReplicatorInflightMsgs(this.maxReplicatorInflightMsgs);
        raftOptions.setDisruptorBufferSize(this.disruptorBufferSize);
        raftOptions.setDisruptorPublishEventWaitTimeoutSecs(this.disruptorPublishEventWaitTimeoutSecs);
        raftOptions.setEnableLogEntryChecksum(this.enableLogEntryChecksum);
        raftOptions.setStartupOldStorage(this.startupOldStorage);
        return raftOptions;
    }

    @Override
    public String toString() {
        return "RaftOptions{" + "maxByteCountPerRpc=" + maxByteCountPerRpc + ", fileCheckHole=" + fileCheckHole
                + ", maxEntriesSize=" + maxEntriesSize + ", maxBodySize=" + maxBodySize + ", maxAppendBufferSize="
                + maxAppendBufferSize + ", maxElectionDelayMs=" + maxElectionDelayMs + ", electionHeartbeatFactor="
                + electionHeartbeatFactor + ", applyBatch=" + applyBatch + ", sync=" + sync + ", syncMeta=" + syncMeta
                + ", openStatistics=" + openStatistics + ", replicatorPipeline=" + replicatorPipeline
                + ", maxReplicatorInflightMsgs=" + maxReplicatorInflightMsgs + ", disruptorBufferSize="
                + disruptorBufferSize + ", disruptorPublishEventWaitTimeoutSecs=" + disruptorPublishEventWaitTimeoutSecs
                + ", enableLogEntryChecksum=" + enableLogEntryChecksum + "," +
                " maxReadIndexLag=" + maxReadIndexLag + ", stepDownWhenVoteTimedout=" + stepDownWhenVoteTimedout
                + ", startUpOldStorage=" + startupOldStorage + '}';
    }
}
