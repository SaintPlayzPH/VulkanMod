package net.vulkanmod.vulkan;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.vulkanmod.Initializer;

public class AndroidRAMInfo {
    
    public static String getMemoryInfo() {
        if (isRunningOnAndroid() && Initializer.CONFIG.showAndroidRAM) {
            Map<String, Long> memoryInfo = readMemoryInfo();

            if (memoryInfo != null) {
                long memTotal = memoryInfo.getOrDefault("MemTotal", 0L);
                long memFree = memoryInfo.getOrDefault("MemAvailable", 0L);
                
                if (memTotal != 0 && memFree != 0) {
                    double memTotalMB = memTotal / 1024.0;
                    double usedMemoryMB = (memTotal - memFree) / 1024.0;
                    return String.format("RAM: %.2f/%.2f MB", usedMemoryMB, memTotalMB);
                }
            }
            return "RAM: Unavailable";
        }
        return "RAM: Unavailable";
    }

    public static String getAvailableMemoryInfo() {
        if (isRunningOnAndroid() && Initializer.CONFIG.showAndroidRAM) {
            Map<String, Long> memoryInfo = readMemoryInfo();

            if (memoryInfo != null) {
                long memTotal = memoryInfo.getOrDefault("MemTotal", 0L);
                long memFree = memoryInfo.getOrDefault("MemAvailable", 0L);
                
                if (memTotal != 0 && memFree != 0) {
                    double memFreeMB = memFree / 1024.0;
                    long freeMemoryPercentage = (memFree * 100) / memTotal;
                    String colorPerc = getColorCodeForMemoryPercentage(freeMemoryPercentage);
                    return String.format("Available RAM: %.2f MB / %s%d%%", memFreeMB, colorPerc, freeMemoryPercentage);
                }
            }
            return "Available RAM: Unavailable";
        }
        return "Available RAM: Unavailable";
    }

    public static String getBuffersInfo() {
        if (isRunningOnAndroid() && Initializer.CONFIG.showAndroidRAM) {
            Map<String, Long> memoryInfo = readMemoryInfo();

            if (memoryInfo != null) {
                long memBuffer = memoryInfo.getOrDefault("Buffers", 0L);
                
                if (memBuffer != 0) {
                    double buffersMB = memBuffer / 1024.0;
                    return String.format("Buffers: %.2f MB", buffersMB);
                }
                return "Buffers: No Buffers";
            }
            return "Buffers: Unavailable";
        }
        return "Buffers: Unavailable";
    }

    private static Map<String, Long> readMemoryInfo() {
        Map<String, Long> memoryInfo = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemTotal") || line.startsWith("MemAvailable") || line.startsWith("Buffers")) {
                    String[] parts = line.split("\\s+");
                    memoryInfo.put(parts[0].replace(":", ""), Long.parseLong(parts[1]));
                }
            }
        } catch (IOException e) {
            Initializer.LOGGER.error("Can't obtain RAM info!");
            return null;
        }

        return memoryInfo;
    }

    private static String getColorCodeForMemoryPercentage(long percentage) {
        if (percentage > 20) return "§a";
        if (percentage >= 16) return "§e";
        if (percentage >= 11) return "§6";
        if (percentage >= 6) return "§c";
        if (percentage >= 0) return "§4";
        return "";
    }

    private static boolean isRunningOnAndroid() {
        return System.getenv("POJAV_RENDERER") != null;
    }
}
