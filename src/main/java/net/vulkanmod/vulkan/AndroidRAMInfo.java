package net.vulkanmod.vulkan;

import net.vulkanmod.Initializer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AndroidRAMInfo {
    public static long highestCurrentUsageRecord = 0;
    public static long highestRAMUsedRecord = 0;
    public static long memBuffers = 0;
    public static long memFree = 0;
    public static long memTotal = 0;
    public static long memUsedDifference = 0;
    public static long prevMemUsed = 0;

    private static final Lock lock = new ReentrantLock();
    private static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
    private static ScheduledFuture<?> memoryUpdateFuture;
    private static ScheduledFuture<?> resetMaxMemoryFuture;
    private static boolean lastResetHighUsageRec;

    static {
        scheduleMemoryUpdateTask();
        lastResetHighUsageRec = Initializer.CONFIG.resetHighUsageRec;
        scheduleResetHighestMemoryUsageRecordTask();

        // Watcher thread for configuration changes
        executorService.scheduleAtFixedRate(AndroidRAMInfo::updateConfigDependentThreads, 0, 1, TimeUnit.SECONDS);
    }

    private static void scheduleMemoryUpdateTask() {
        if (memoryUpdateFuture != null && !memoryUpdateFuture.isCancelled()) {
            memoryUpdateFuture.cancel(true);
        }
        memoryUpdateFuture = executorService.scheduleAtFixedRate(
            AndroidRAMInfo::getAllMemoryInfo,
            0,
            Initializer.CONFIG.ramInfoUpdate == 0 ? 10 : Initializer.CONFIG.ramInfoUpdate * 100,
            TimeUnit.MILLISECONDS
        );
    }

    private static void scheduleResetHighestMemoryUsageRecordTask() {
        if (resetMaxMemoryFuture != null && !resetMaxMemoryFuture.isCancelled()) {
            resetMaxMemoryFuture.cancel(true);
        }
        if (Initializer.CONFIG.resetHighUsageRec) {
            resetMaxMemoryFuture = executorService.scheduleAtFixedRate(
                AndroidRAMInfo::resetHighestUsageRecord,
                0,
                45,
                TimeUnit.SECONDS
            );
        }
    }

    private static void updateConfigDependentThreads() {
        boolean configChanged = false;
        if (Initializer.CONFIG.resetHighUsageRec != lastResetHighUsageRec) {
            scheduleResetHighestMemoryUsageRecordTask();
            lastResetHighUsageRec = Initializer.CONFIG.resetHighUsageRec;
            configChanged = true;
        }
        // Only schedule a new memory update task if the configuration has changed
        if (configChanged || lastRamInfoUpdate != Initializer.CONFIG.ramInfoUpdate) {
            lastRamInfoUpdate = Initializer.CONFIG.ramInfoUpdate; // Store the last update value
            scheduleMemoryUpdateTask();  // This ensures the update interval is dynamically adjusted
        }
    }

    public static void getAllMemoryInfo() {
        if (isRunningOnAndroid() && Initializer.CONFIG.showAndroidRAM) {
            try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
                String line;
                lock.lock();
                try {
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("MemTotal")) {
                            memTotal = extractMemoryValue(line);
                        } else if (line.startsWith("MemAvailable")) {
                            memFree = extractMemoryValue(line);
                        } else if (line.startsWith("Buffers")) {
                            memBuffers = extractMemoryValue(line);
                        }
                    }

                    // Update the current memory used and max memory used
                    long currentMemUsed = memTotal - memFree;

                    // Calculate the memory used difference
                    memUsedDifference = currentMemUsed - prevMemUsed;
                    prevMemUsed = currentMemUsed;

                    // Update the max memory used per second
                    if (memUsedDifference > highestCurrentUsageRecord) {
                        highestCurrentUsageRecord = memUsedDifference;
                    }

                    // Compare highestCurrentUsageRecord with currentMemUsed and threshold
                    if (highestCurrentUsageRecord > currentMemUsed || highestCurrentUsageRecord > (currentMemUsed - 200)) {
                        resetHighestUsageRecord();
                    }

                    // Update the max memory used
                    if (currentMemUsed > highestRAMUsedRecord) {
                        highestRAMUsedRecord = currentMemUsed;
                    }
                } finally {
                    lock.unlock();
                }
            } catch (IOException e) {
                Initializer.LOGGER.error("Can't obtain RAM info: " + e.getMessage(), e);
            }
        }
    }

    public static String getMemoryInfo() {
        lock.lock();
        try {
            if (memTotal != 0 && memFree != 0) {
                double memTotalMB = memTotal / 1024.0;
                double usedMemoryMB = (memTotal - memFree) / 1024.0;
                return String.format("RAM: %.2f/%.2f MB", usedMemoryMB, memTotalMB);
            }
        } finally {
            lock.unlock();
        }
        return "RAM: Unavailable";
    }

    public static String getRAMInfo() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            return br.lines()
                    .filter(line -> line.startsWith("MemTotal"))
                    .map(line -> {
                        long sizeKB = Long.parseLong(line.split("\\s+")[1]);
                        double sizeGB = sizeKB / 1048576.0;
                        return String.format("%.2f GB", sizeGB);
                    })
                    .findFirst()
                    .orElse("Unknown");
        } catch (IOException e) {
            Initializer.LOGGER.error("Can't obtain your RAM!", e);
            return "Unknown";
        }
    }

    public static String getCurrentUsage() {
        lock.lock();
        try {
            if (prevMemUsed != 0) {
                double memUsedDiffMB = memUsedDifference / 1024.0;
                String color = memUsedDifference > 0 ? "§c↑" : memUsedDifference < 0 ? "§a↓" : "";
                return String.format("Current Usage: %s%.2f MB", color, Math.abs(memUsedDiffMB));
            }
        } finally {
            lock.unlock();
        }
        return "Current Usage: Unavailable";
    }

    public static String getAvailableMemoryInfo() {
        lock.lock();
        try {
            if (memTotal != 0 && memFree != 0) {
                double memFreeMB = memFree / 1024.0;
                long freeMemoryPercentage = (memFree * 100) / memTotal;
                String colorPerc = getColorPercentage(freeMemoryPercentage);
                return String.format("Available RAM: %.2f MB / %s%d%%", memFreeMB, colorPerc, freeMemoryPercentage);
            }
        } finally {
            lock.unlock();
        }
        return "Available RAM: Unavailable";
    }

    public static String getHighestMemoryUsedRecord() {
        lock.lock();
        try {
            String highestCurrentUsageRecorded = "Highest Usage: Unavailable";
            String highestRAMUsedRecorded = "Highest RAM Used: Unavailable";

            if (highestCurrentUsageRecord != 0) {
                double highestCurrentUsageRecordMB = highestCurrentUsageRecord / 1024.0;
                String color = highestCurrentUsageRecord > 0 ? "§c↑" : "§a↓";
                highestCurrentUsageRecorded = String.format("Highest Usage: %s%.2f MB", color, Math.abs(highestCurrentUsageRecordMB));
            }

            if (highestRAMUsedRecord != 0) {
                double highestRAMUsedRecordedMB = highestRAMUsedRecord / 1024.0;
                highestRAMUsedRecorded = String.format("Highest RAM Used: %.2f MB", highestRAMUsedRecordedMB);
            }

            return highestCurrentUsageRecorded + "§r / " + highestRAMUsedRecorded;
        } finally {
            lock.unlock();
        }
    }

    public static String getBuffersInfo() {
        lock.lock();
        try {
            if (memBuffers != 0) {
                double buffersMB = memBuffers / 1024.0;
                return String.format("Buffers: %.2f MB", buffersMB);
            }
        } finally {
            lock.unlock();
        }
        return "Buffers: No Buffers";
    }

    private static long extractMemoryValue(String memoryLine) {
        return Long.parseLong(memoryLine.split("\\s+")[1]);
    }

    private static boolean isRunningOnAndroid() {
        return System.getenv("POJAV_ENVIRON") != null || System.getenv("SCL_ENVIRON") != null || System.getenv("POJAV_RENDERER") != null;
    }

    public static String getAvailableRAMWarn() {
        lock.lock();
        try {
            double memFreeMB = memFree / 1024.0;
            if (memFreeMB < 500) {
                return memFreeMB > 300 ? "RAM running low, the game will start to lag." : "RAM running very low, the game will lag significantly and has a chance to crash.";
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

    private static void resetHighestUsageRecord() {
        lock.lock();
        try {
            highestRAMUsedRecord = 0;
            highestCurrentUsageRecord = 0;
        } finally {
            lock.unlock();
        }
    }
}
