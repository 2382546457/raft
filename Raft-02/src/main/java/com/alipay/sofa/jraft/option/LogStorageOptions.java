package com.alipay.sofa.jraft.option;

import com.alipay.sofa.jraft.conf.ConfigurationManager;
import com.alipay.sofa.jraft.entity.codec.LogEntryCodecFactory;

/**
 * @author : 小何
 * @Description : 日志存储器需要的配置
 * @date : 2024-04-04 21:24
 */
public class LogStorageOptions {
    private String groupId;
    private ConfigurationManager configurationManager;

    private LogEntryCodecFactory logEntryCodecFactory;

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public ConfigurationManager getConfigurationManager() {
        return configurationManager;
    }

    public void setConfigurationManager(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    public LogEntryCodecFactory getLogEntryCodecFactory() {
        return logEntryCodecFactory;
    }

    public void setLogEntryCodecFactory(LogEntryCodecFactory logEntryCodecFactory) {
        this.logEntryCodecFactory = logEntryCodecFactory;
    }
}
