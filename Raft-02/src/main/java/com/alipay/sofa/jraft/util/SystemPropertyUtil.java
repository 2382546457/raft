package com.alipay.sofa.jraft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * 如果已经读了手写Spring的第3篇文章，应该对这个工具类的作用了如指掌了
 */
public final class SystemPropertyUtil {

    private static final Logger LOG = LoggerFactory.getLogger(SystemPropertyUtil.class);


    public static boolean contains(String key) {
        return get(key) != null;
    }


    public static String get(String key) {
        return get(key, null);
    }


    public static String get(final String key, String def) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty.");
        }

        String value = null;
        try {
            if (System.getSecurityManager() == null) {
                value = System.getProperty(key);
            } else {
                value = AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getProperty(key));
            }
        } catch (Exception e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Unable to retrieve a system property '{}'; default values will be used, {}.", key, e);
            }
        }

        if (value == null) {
            return def;
        }

        return value;
    }


    public static boolean getBoolean(String key, boolean def) {
        String value = get(key);
        if (value == null) {
            return def;
        }

        value = value.trim().toLowerCase();
        if (value.isEmpty()) {
            return true;
        }

        if ("true".equals(value) || "yes".equals(value) || "1".equals(value)) {
            return true;
        }

        if ("false".equals(value) || "no".equals(value) || "0".equals(value)) {
            return false;
        }

        LOG.warn("Unable to parse the boolean system property '{}':{} - using the default value: {}.", key, value, def);

        return def;
    }

    public static int getInt(String key, int def) {
        String value = get(key);
        if (value == null) {
            return def;
        }

        value = value.trim().toLowerCase();
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            // ignored
        }

        LOG.warn("Unable to parse the integer system property '{}':{} - using the default value: {}.", key, value, def);

        return def;
    }

    public static long getLong(String key, long def) {
        String value = get(key);
        if (value == null) {
            return def;
        }

        value = value.trim().toLowerCase();
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            // ignored
        }

        LOG.warn("Unable to parse the long integer system property '{}':{} - using the default value: {}.", key, value,
                def);

        return def;
    }

    public static Object setProperty(String key, String value) {
        return System.getProperties().setProperty(key, value);
    }

    private SystemPropertyUtil() {
    }
}
