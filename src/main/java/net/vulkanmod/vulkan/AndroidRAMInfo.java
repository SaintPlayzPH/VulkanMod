package net.vulkanmod.vulkan;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.vulkanmod.Initializer;

public class AndroidRAMInfo {
    public static long memFree = 0;
    public static long memTotal = 0;
    public static long memBuffers = 0;
    public static long maxMemUsed = 0;
    public static long prevMemUsed = 0;
    public static long memUsedDifference = 0;
    public static long maxMemUsedPerSecond = 0;

    private static final Lock lock = new ReentrantLock();
    private static Thread resetMaxMemoryThread;
    private static boolean lastResetHighUsageRec;

    static {
        Thread memoryUpdateThread = new Thread(() -> {
            while (true) {
                getAllMemoryInfo();
                try {
                    Thread.sleep(Initializer.CONFIG.ramInfoUpdate == 0 ? 10 : Initializer.CONFIG.ramInfoUpdate * 100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        memoryUpdateThread.setDaemon(true);
        memoryUpdateThread.start();

        lastResetHighUsageRec = Initializer.CONFIG.resetHighUsageRec;
        initializeResetMaxMemoryThread();

        Thread configWatcherThread = new Thread(() -> {
            while (true) {
                if (Initializer.CONFIG.resetHighUsageRec != lastResetHighUsageRec) {
                    updateResetMaxMemoryThread();
                    lastResetHighUsageRec = Initializer.CONFIG.resetHighUsageRec;
                }
                try {
                    Thread.sleep(1000); // Check for changes every second
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        configWatcherThread.setDaemon(true);
        configWatcherThread.start();
    }

    private static void initializeResetMaxMemoryThread() {
        if (resetMaxMemoryThread != null && resetMaxMemoryThread.isAlive()) {
            resetMaxMemoryThread.interrupt();
        }

        if (Initializer.CONFIG.resetHighUsageRec) {
            resetMaxMemoryThread = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(45000); // reset every 45 seconds
                        resetMaxMemoryUsage();
                        resetMaxMemoryUsagePerSecond();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            resetMaxMemoryThread.setDaemon(true);
            resetMaxMemoryThread.start();
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
                    if (currentMemUsed > maxMemUsed) {
                        maxMemUsed = currentMemUsed;
                    }

                    // Calculate the memory used difference
                    memUsedDifference = currentMemUsed - prevMemUsed;
                    prevMemUsed = currentMemUsed;

                    // Update the max memory used per second
                    if (memUsedDifference > maxMemUsedPerSecond) {
                        maxMemUsedPerSecond = memUsedDifference;
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
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemTotal")) {
                    return line.split("\\s+")[1];
                }
            }
        } catch (IOException e) {
            Initializer.LOGGER.error("Can't obtain your RAM!");
        }
        return "Unknown";
    }

    public static String getHighestMemoryAndRAMUsage() {
        lock.lock();
        try {
            String highestMemoryUsagePerSecond;
            if (maxMemUsedPerSecond != 0) {
                double maxMemUsedPerSecondMB = maxMemUsedPerSecond / 1024.0;
                String color;
                if (maxMemUsedPerSecond > 0) {
                    color = "§c↑";
                } else if (maxMemUsedPerSecond < 0) {
                    color = "§a↓";
                } else {
                    color = "";
                }
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

    public static String getMemoryUsagePerSecond() {
        lock.lock();
        try {
            if (prevMemUsed != 0) {
                double memUsedDiffMB = memUsedDifference / 1024.0;
                String color;
                if (memUsedDifference > 0) {
                    color = "§c↑";
                } else if (memUsedDifference < 0) {
                    color = "§a↓";
                } else {
                    color = "";
                }
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
        if (System.getenv("POJAV_ENVIRON") != null) { // PojavLauncher
            return true;
        }
        if (System.getenv("SCL_ENVIRON") != null) { // SolCraftLauncher
            return true;
        }
        return System.getenv("POJAV_RENDERER") != null;
    }

    public static String getAvailableRAMWarn() {
        double memFreeMB = memFree / 1024.0;
        lock.lock();
        try {
            if (memFreeMB < 500) {
                if (memFreeMB > 300) {
                    return "RAM running low, the game will start to lag.";
                } else {
                    return "RAM running very low, the game will lag significantly and has a chance to crash.";
                }
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

    public static void updateResetMaxMemoryThread() {
        initializeResetMaxMemoryThread();
    }

    public static void updateConfigDependentThreads() {
        // This method should be called when the config changes
        if (Initializer.CONFIG.resetHighUsageRec != lastResetHighUsageRec) {
            updateResetMaxMemoryThread();
            lastResetHighUsageRec = Initializer.CONFIG.resetHighUsageRec;
        }
    }
}
