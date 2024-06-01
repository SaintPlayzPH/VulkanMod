package net.vulkanmod.vulkan;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.vulkanmod.Initializer;

public class AndroidRAMInfo {
    private static long memFree = 0;
    private static long memTotal = 0;
    private static long memBuffers = 0;

    private static final Lock lock = new ReentrantLock();

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
        return System.getenv("POJAV_RENDERER") != null;
    }

    private static String getAvailableRAMWarn() {
        getAllMemoryInfo();
        lock.lock();
        try {
            if (memFreeMB < 500) {
                if (memFreeMB > 300) {
                    return "Your RAM is low, the system will start to lag at this point.";
                } else {
                    return "Your RAM is very low, the system will lag significantly and there's a chance the game may force crash.";
                }
            }
        } finally {
            lock.unlock();
        }
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
}
