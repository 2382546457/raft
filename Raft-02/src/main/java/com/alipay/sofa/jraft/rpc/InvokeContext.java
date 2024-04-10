package com.alipay.sofa.jraft.rpc;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author : 小何
 * @Description : 执行上下文
 * @date : 2024-04-04 14:20
 */
public class InvokeContext {
    public final static String CRC_SWITCH = "invoke.crc.switch";

    private final ConcurrentMap<String, Object> ctx = new ConcurrentHashMap<>();

    public Object put(final String key, final Object value) {
        return this.ctx.put(key, value);
    }

    public Object putIfAbsent(final String key, final Object value) {
        return this.ctx.putIfAbsent(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(final String key) {
        return (T) this.ctx.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(final String key, final T defaultValue) {
        return (T) this.ctx.getOrDefault(key, defaultValue);
    }

    public void clear() {
        this.ctx.clear();
    }

    public Set<Map.Entry<String, Object>> entrySet() {
        return this.ctx.entrySet();
    }
}
