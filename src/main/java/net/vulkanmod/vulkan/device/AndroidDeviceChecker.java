package net.vulkanmod.vulkan.device;

import java.io.File;

public class AndroidDeviceChecker {
    public static boolean isCPUInfoAvailable() {
        File cpuInfoFile = new File("/proc/cpuinfo");
        return cpuInfoFile.exists() && cpuInfoFile.canRead();
    }

    public static boolean isRunningOnCompatDevice() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("linux") || osName.contains("android");
    }

    public static boolean isRunningOnAndroid() {
        String osName = System.getProperty("os.name").toLowerCase();
        return (osName.contains("linux") || osName.contains("android")) && (System.getenv("POJAV_ENVIRON") != null ||
               System.getenv("SCL_ENVIRON") != null ||
               System.getenv("POJAV_RENDERER") != null);
    }
}
