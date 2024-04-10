package com.alipay.sofa.jraft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;


//检测运行平台的工具类
public class Platform {

    private static final Logger LOG        = LoggerFactory.getLogger(Platform.class);

    private static final boolean IS_WINDOWS = isWindows0();

    private static final boolean IS_MAC     = isMac0();

    private static final boolean IS_LINUX   = isLinux0();

    /**
     * Return {@code true} if the JVM is running on Windows
     */
    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * Return {@code true} if the JVM is running on Mac OSX
     */
    public static boolean isMac() {
        return IS_MAC;
    }

    /**
     * Return {@code true} if the JVM is running on Linux OSX
     */
    public static boolean isLinux() {
        return IS_LINUX;
    }

    private static boolean isMac0() {
        final boolean mac = SystemPropertyUtil.get("os.name", "") //
                .toLowerCase(Locale.US) //
                .contains("mac os x");
        if (mac) {
            LOG.debug("Platform: Mac OS X");
        }
        return mac;
    }

    private static boolean isWindows0() {
        final boolean windows = SystemPropertyUtil.get("os.name", "") //
                .toLowerCase(Locale.US) //
                .contains("win");
        if (windows) {
            LOG.debug("Platform: Windows");
        }
        return windows;
    }

    private static boolean isLinux0() {
        final boolean linux = SystemPropertyUtil.get("os.name", "") //
                .toLowerCase(Locale.US) //
                .contains("linux");
        if (linux) {
            LOG.debug("Platform: Linux");
        }
        return linux;
    }
}
