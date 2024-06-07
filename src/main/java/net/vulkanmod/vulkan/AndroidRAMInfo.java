package net.vulkanmod.vulkan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.vulkanmod.Initializer;

public class AndroidRAMInfo {
    private static long memFree = 0;
    private static long memTotal = 0;
    private static long memBuffers = 0;
    private static long maxMemUsed = 0;
    private static long prevMemUsed = 0;
    private static long memUsedDifference = 0;
    private static long maxMemUsedPerSecond = 0;

    private static final Lock lock = new ReentrantLock();
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static boolean lastResetHighUsageRec;

    static {
        scheduleMemoryUpdateTask();
        lastResetHighUsageRec = Initializer.CONFIG.resetHighUsageRec;
        scheduleConfigWatcherTask();
    }

    private static void scheduleMemoryUpdateTask() {
        long updateInterval = Initializer.CONFIG.ramInfoUpdate == 0 ? 10 : Initializer.CONFIG.ramInfoUpdate * 100;
        scheduler.scheduleAtFixedRate(AndroidRAMInfo::getAllMemoryInfo, 0, updateInterval, TimeUnit.MILLISECONDS);
    }

    private static void scheduleConfigWatcherTask() {
        scheduler.scheduleAtFixedRate(() -> {
            if (Initializer.CONFIG.resetHighUsageRec != lastResetHighUsageRec) {
                updateResetMaxMemoryThread();
                lastResetHighUsageRec = Initializer.CONFIG.resetHighUsageRec;
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private static void updateResetMaxMemoryThread() {
        if (Initializer.CONFIG.resetHighUsageRec) {
            scheduler.scheduleAtFixedRate(() -> {
                resetMaxMemoryUsage();
                resetMaxMemoryUsagePerSecond();
            }, 0, 45, TimeUnit.SECONDS);
        }
    }

    public static void getAllMemoryInfo() {
        if (isRunningOnAndroid() && Initializer.CONFIG.showAndroidRAM) {
            try {
                lock.lock();
                Files.lines(Paths.get("/proc/meminfo")).forEach(line -> {
                    if (line.startsWith("MemTotal")) {
                        memTotal = extractMemoryValue(line);
                    } else if (line.startsWith("MemAvailable")) {
                        memFree = extractMemoryValue(line);
                    } else if (line.startsWith("Buffers")) {
                        memBuffers = extractMemoryValue(line);
                    }
                });

                long currentMemUsed = memTotal - memFree;
                maxMemUsed = Math.max(maxMemUsed, currentMemUsed);
                memUsedDifference = currentMemUsed - prevMemUsed;
                prevMemUsed = currentMemUsed;
                maxMemUsedPerSecond = Math.max(maxMemUsedPerSecond, memUsedDifference);
            } catch (IOException e) {
                Initializer.LOGGER.error("Can't obtain RAM info: " + e.getMessage());
            } finally {
                lock.unlock();
            }
        }
    }

    public static String getMemoryInfo() {
        lock.lock();
        try {
            return (memTotal != 0 && memFree != 0) ?
                    String.format("RAM: %.2f/%.2f MB", (memTotal - memFree) / 1024.0, memTotal / 1024.0) :
                    "RAM: Unavailable";
        } finally {
            lock.unlock();
        }
    }

    public static String getHighestMemoryAndRAMUsage() {
        lock.lock();
        try {
            double maxMemUsedPerSecondMB = maxMemUsedPerSecond / 1024.0;
            String highestMemoryUsagePerSecond = (maxMemUsedPerSecond != 0) ?
                    String.format("Highest Usage: %s%.2f MB", getColor(maxMemUsedPerSecond), maxMemUsedPerSecondMB) :
                    "Highest Usage: Unavailable";

            double maxMemUsedMB = maxMemUsed / 1024.0;
            String highestRAMUsage = (maxMemUsed != 0) ?
                    String.format("Highest RAM Used: %.2f MB", maxMemUsedMB) :
                    "Highest RAM Used: Unavailable";

            return highestMemoryUsagePerSecond + "§r / " + highestRAMUsage;
        } finally {
            lock.unlock();
        }
    }

    public static String getMemoryUsagePerSecond() {
        lock.lock();
        try {
            double memUsedDiffMB = memUsedDifference / 1024.0;
            return (prevMemUsed != 0) ?
                    String.format("Current Usage: %s%.2f MB", getColor(memUsedDifference), memUsedDiffMB) :
                    "Current Usage: Unavailable";
        } finally {
            lock.unlock();
        }
    }

    public static String getAvailableMemoryInfo() {
        lock.lock();
        try {
            if (memTotal != 0 && memFree != 0) {
                double memFreeMB = memFree / 1024.0;
                long freeMemoryPercentage = (memFree * 100) / memTotal;
                return String.format("Available RAM: %.2f MB / %s%d%%", memFreeMB, getColorPercentage(freeMemoryPercentage), freeMemoryPercentage);
            } else {
                return "Available RAM: Unavailable";
            }
        } finally {
            lock.unlock();
        }
    }

    public static String getBuffersInfo() {
        lock.lock();
        try {
            return (memBuffers != 0) ?
                    String.format("Buffers: %.2f MB", memBuffers / 1024.0) :
                    "Buffers: No Buffers";
        } finally {
            lock.unlock();
        }
    }

    private static long extractMemoryValue(String memoryLine) {
        String[] parts = memoryLine.split("\\s+");
        return Long.parseLong(parts[1]);
    }

    private static boolean isRunningOnAndroid() {
        return System.getenv("POJAV_ENVIRON") != null || System.getenv("SCL_ENVIRON") != null || System.getenv("POJAV_RENDERER") != null;
    }

    public static String getAvailableRAMWarn() {
        lock.lock();
        try {
            double memFreeMB = memFree / 1024.0;
            if (memFreeMB < 500) {
                return (memFreeMB > 300) ? "RAM running low, the game will start to lag." :
                        "RAM running very low, the game will lag significantly and has a chance to crash.";
            }
        } finally {
            lock.unlock();
        }
        return "";
    }

    private static String getColorPercentage(long freeMemoryPercentage) {
        if (freeMemoryPercentage > 20) return "§a";
        if (freeMemoryPercentage >= 16) return "§e";
        if (freeMemoryPercentage >= 11) return "§6";
        if (freeMemoryPercentage >= 6) return "§c";
        return "§4";
    }

    private static void resetMaxMemoryUsage() {
        lock.lock();
        try {
            maxMemUsed = 1;
        } finally {
            lock.unlock();
        }
    }

    private static void resetMaxMemoryUsagePerSecond() {
        lock.lock();
        try {
            maxMemUsedPerSecond = 1;
        } finally {
            lock.unlock();
        }
    }

    private static String getColor(long value) {
        return value > 0 ? "§c↑" : value < 0 ? "§a↓" : "";
    }
}
