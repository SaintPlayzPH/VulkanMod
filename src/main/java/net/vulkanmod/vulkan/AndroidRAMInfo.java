package net.vulkanmod.vulkan;

import net.vulkanmod.Initializer;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AndroidRAMInfo {
    private static volatile long memFree = 0;
    private static volatile long memTotal = 0;
    private static volatile long memBuffers = 0;
    private static volatile long maxMemUsed = 0;
    private static volatile long prevMemUsed = 0;
    private static volatile long memUsedDifference = 0;
    private static volatile long maxMemUsedPerSecond = 0;

    private static final Lock lock = new ReentrantLock();
    private static ScheduledExecutorService scheduler;
    private static boolean lastResetHighUsageRec;

    static {
        scheduler = Executors.newScheduledThreadPool(2);

        Runnable memoryUpdateTask = new Runnable() {
            @Override
            public void run() {
                getAllMemoryInfo();
                long delay = Initializer.CONFIG.ramInfoUpdate == 0 ? 10 : Initializer.CONFIG.ramInfoUpdate * 100;
                scheduler.schedule(this, delay, TimeUnit.MILLISECONDS);
            }
        };
        scheduler.schedule(memoryUpdateTask, 0, TimeUnit.MILLISECONDS);

        lastResetHighUsageRec = Initializer.CONFIG.resetHighUsageRec;
        initializeResetMaxMemoryThread();

        Runnable configWatcherTask = new Runnable() {
            @Override
            public void run() {
                if (Initializer.CONFIG.resetHighUsageRec != lastResetHighUsageRec) {
                    updateResetMaxMemoryThread();
                    lastResetHighUsageRec = Initializer.CONFIG.resetHighUsageRec;
                }
                scheduler.schedule(this, 100, TimeUnit.MILLISECONDS);
            }
        };
        scheduler.schedule(configWatcherTask, 0, TimeUnit.MILLISECONDS);
    }

    private static void initializeResetMaxMemoryThread() {
        if (resetMaxMemoryThread != null && resetMaxMemoryThread.isAlive()) {
            resetMaxMemoryThread.interrupt();
        }

        if (Initializer.CONFIG.resetHighUsageRec) {
            Runnable resetMaxMemoryTask = new Runnable() {
                @Override
                public void run() {
                    resetMaxMemoryUsageRecord();
                    scheduler.schedule(this, 45, TimeUnit.SECONDS);
                }
            };
            scheduler.schedule(resetMaxMemoryTask, 0, TimeUnit.MILLISECONDS);
        }
    }

    public static void getAllMemoryInfo() {
        if (isRunningOnAndroid() && Initializer.CONFIG.showAndroidRAM) {
            try (BufferedReader br = Files.newBufferedReader(Paths.get("/proc/meminfo"))) {
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

                    long currentMemUsed = memTotal - memFree;
                    memUsedDifference = currentMemUsed - prevMemUsed;
                    prevMemUsed = currentMemUsed;

                    if (memUsedDifference > maxMemUsedPerSecond) {
                        maxMemUsedPerSecond = memUsedDifference;
                    }

                    if (maxMemUsedPerSecond > currentMemUsed || maxMemUsedPerSecond > (currentMemUsed - 200)) {
                        resetMaxMemoryUsageRecord();
                    }

                    if (currentMemUsed > maxMemUsed) {
                        maxMemUsed = currentMemUsed;
                    }
                } finally {
                    lock.unlock();
                }
            } catch (IOException e) {
                Initializer.LOGGER.error("Can't obtain RAM info: " + e.getMessage());
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
            } else {
                return "RAM: Unavailable";
            }
        } finally {
            lock.unlock();
        }
    }

    public static String getRAMInfo() {
        try (BufferedReader br = Files.newBufferedReader(Paths.get("/proc/meminfo"))) {
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
            Initializer.LOGGER.error("Can't obtain your RAM!");
            return "Unknown";
        }
    }

    public static String getMemoryUsagePerSecond() {
        lock.lock();
        try {
            if (prevMemUsed != 0) {
                double memUsedDiffMB = memUsedDifference / 1024.0;
                String color = memUsedDifference > 0 ? "§c↑" : memUsedDifference < 0 ? "§a↓" : "";
                return String.format("Current Usage: %s%.2f MB", color, Math.abs(memUsedDiffMB));
            } else {
                return "Current Usage: Unavailable";
            }
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
                String colorPerc = getColorPercentage(freeMemoryPercentage);
                return String.format("Available RAM: %.2f MB / %s%d%%", memFreeMB, colorPerc, freeMemoryPercentage);
            } else {
                return "Available RAM: Unavailable";
            }
        } finally {
            lock.unlock();
        }
    }

    public static String getHighestMemoryAndRAMUsage() {
        lock.lock();
        try {
            String highestMemoryUsagePerSecond;
            if (maxMemUsedPerSecond != 0) {
                double maxMemUsedPerSecondMB = maxMemUsedPerSecond / 1024.0;
                String color = maxMemUsedPerSecond > 0 ? "§c↑" : maxMemUsedPerSecond < 0 ? "§a↓" : "";
                highestMemoryUsagePerSecond = String.format("Highest Usage: %s%.2f MB", color, Math.abs(maxMemUsedPerSecondMB));
            } else {
                highestMemoryUsagePerSecond = "Highest Usage: Unavailable";
            }

            String highestRAMUsage;
            if (maxMemUsed != 0) {
                double maxMemUsedMB = maxMemUsed / 1024.0;
                highestRAMUsage = String.format("Highest RAM Used: %.2f MB", maxMemUsedMB);
            } else {
                highestRAMUsage = "Highest RAM Used: Unavailable";
            }

            return highestMemoryUsagePerSecond + "§r / " + highestRAMUsage;
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
            } else {
                return "Buffers: No Buffers";
            }
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
        double memFreeMB = memFree / 1024.0;
        lock.lock();
        try {
            if (memFreeMB < 500) {
                return memFreeMB > 300 ? "RAM running low, the game will start to lag." : "RAM running very low, the game will lag significantly and has a chance to crash.";
            }
        } finally {
            lock.unlock();
        }
        return "";
    }

    private static String getColorPercentage(long freeMemoryPercentage) {
        if (freeMemoryPercentage > 20) {
            return "§a";
        } else if (freeMemoryPercentage >= 16) {
            return "§e";
        } else if (freeMemoryPercentage >= 11) {
            return "§6";
        } else if (freeMemoryPercentage >= 6) {
            return "§c";
        } else {
            return "§4";
        }
    }

    private static void resetMaxMemoryUsageRecord() {
        lock.lock();
        try {
            maxMemUsed = 0;
            maxMemUsedPerSecond = 0;
        } finally {
            lock.unlock();
        }
    }

    public static void updateResetMaxMemoryThread() {
        initializeResetMaxMemoryThread();
    }

    public static void updateConfigDependentThreads() {
        if (Initializer.CONFIG.resetHighUsageRec != lastResetHighUsageRec) {
            updateResetMaxMemoryThread();
            lastResetHighUsageRec = Initializer.CONFIG.resetHighUsageRec;
        }
    }
}
