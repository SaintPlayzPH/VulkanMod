package net.vulkanmod.vulkan.device;

public class AndroidDeviceChecker {
    public static boolean isRunningOnAndroid() {
        String osName = System.getProperty("os.name").toLowerCase();
        return (osName.contains("linux") || osName.contains("android")) && (System.getenv("POJAV_ENVIRON") != null ||
               System.getenv("SCL_ENVIRON") != null ||
               System.getenv("POJAV_RENDERER") != null);
    }
}
