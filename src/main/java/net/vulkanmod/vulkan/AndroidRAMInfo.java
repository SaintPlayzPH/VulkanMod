package net.vulkanmod.vulkan;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import net.vulkanmod.Initializer;

public class AndroidRAMInfo {
    
    public static String getMemoryInfo() {
        if (isRunningOnAndroid() && Initializer.CONFIG.showAndroidRAM) {
            long memFree = 0;
            long memTotal = 0;

            try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("MemTotal")) {
                        memTotal = extractMemoryValue(line);
                    } else if (line.startsWith("MemAvailable")) {
                        memFree = extractMemoryValue(line);
                    }
                }
            } catch (IOException e) {
                Initializer.LOGGER.error("Can't obtain RAM info!");
                return "RAM: Unavailable";
            }

            if (memTotal != 0 && memFree != 0) {
                double memTotalMB = memTotal / 1024.0;
                double usedMemoryMB = (memTotal - memFree) / 1024.0;
                return "RAM: " + String.format("%.2f", usedMemoryMB) + "/" + String.format("%.2f", memTotalMB) + " MB";
            } else {
                return "RAM: Unavailable";
            }
        }
        return "RAM: Unavailable";
    }

    public static String getAvailableMemoryInfo() {
        if (isRunningOnAndroid() && Initializer.CONFIG.showAndroidRAM) {
            long memFree = 0;
            long memTotal = 0;

            try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("MemTotal")) {
                        memTotal = extractMemoryValue(line);
                    } else if (line.startsWith("MemAvailable")) {
                        memFree = extractMemoryValue(line);
                    }
                }
            } catch (IOException e) {
                Initializer.LOGGER.error("Can't obtain Available RAM info!");
                return "Available RAM: Unavailable";
            }

            if (memTotal != 0 && memFree != 0) {
                double memFreeMB = memFree / 1024.0;
                long freeMemoryPercentage = (memFree * 100) / memTotal;
                String colorPerc;
                if (freeMemoryPercentage > 20) {
                    colorPerc = "§a";
                } else if (freeMemoryPercentage >= 16 && freeMemoryPercentage <= 20) {
                    colorPerc = "§e";
                } else if (freeMemoryPercentage >= 11 && freeMemoryPercentage <= 15) {
                    colorPerc = "§6";
                } else if (freeMemoryPercentage >= 6 && freeMemoryPercentage <= 10) {
                    colorPerc = "§c";
                } else if (freeMemoryPercentage >= 0 && freeMemoryPercentage <= 5) {
                    colorPerc = "§4";
                } else {
                    colorPerc = "";
                }
                 return "Available RAM: " + String.format("%.2f", memFreeMB) + " MB / "  + colorPerc + freeMemoryPercentage + "%";
            } else {
                return "Available RAM: Ran Out of Available Memory";
            }
        }
        return "Available RAM: Unavailable";
    }

    public static String getBuffersInfo() {
        if (isRunningOnAndroid() && Initializer.CONFIG.showAndroidRAM) {
            long memBuffer = 0;

            try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("Buffers")) {
                        memBuffer = extractMemoryValue(line);
                        break;
                    }
                }
            } catch (IOException e) {
                Initializer.LOGGER.error("Can't obtain Buffers info!");
                return "Buffers: Unavailable";
            }

            if (memBuffer != 0) {
                double buffersMB = memBuffer / 1024.0;
                return "Buffers: " + String.format("%.2f", buffersMB) + " MB";
            } else {
                return "Buffers: No Buffers";
            }
        }
        return "Buffers: Unavailable";
    }

    private static long extractMemoryValue(String memoryLine) {
        String[] parts = memoryLine.split("\\s+");
        return Long.parseLong(parts[1]);
    }

    private static boolean isRunningOnAndroid() {
        return System.getenv("POJAV_RENDERER") != null;
    }
}
