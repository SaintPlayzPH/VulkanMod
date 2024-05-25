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

            try (BufferedReader br = new BufferedReader(new FileReader("\u002F\u0070\u0072\u006F\u0063\u002F\u006D\u0065\u006D\u0069\u006E\u0066\u006F"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("\u004D\u0065\u006D\u0054\u006F\u0074\u0061\u006C")) {
                        memTotal = extractMemoryValue(line);
                    } else if (line.startsWith("\u004D\u0065\u006D\u0041\u0076\u0061\u0069\u006C\u0061\u0062\u006C\u0065")) {
                        memFree = extractMemoryValue(line);
                    }
                }
            } catch (IOException e) {
                Initializer.LOGGER.error("Can't obtain Memory info!");
                return "Memory: Unavailable";
            }

            if (memTotal != 0 && memFree != 0) {
                double memTotalMB = memTotal / 1024.0;
                double usedMemoryMB = (memTotal - memFree) / 1024.0;
                return "Memory: " + String.format("%.2f", usedMemoryMB) + "/" + String.format("%.2f", memTotalMB) + " MB";
            } else {
                return "Memory: Unavailable";
            }
        }
        return "Memory: Unavailable";
    }

    public static String getAvailableMemoryInfo() {
        if (isRunningOnAndroid() && Initializer.CONFIG.showAndroidRAM) {
            long memFree = 0;
            long memTotal = 0;

            try (BufferedReader br = new BufferedReader(new FileReader("\u002F\u0070\u0072\u006F\u0063\u002F\u006D\u0065\u006D\u0069\u006E\u0066\u006F"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("\u004D\u0065\u006D\u0054\u006F\u0074\u0061\u006C")) {
                        memTotal = extractMemoryValue(line);
                    } else if (line.startsWith("\u004D\u0065\u006D\u0041\u0076\u0061\u0069\u006C\u0061\u0062\u006C\u0065")) {
                        memFree = extractMemoryValue(line);
                    }
                }
            } catch (IOException e) {
                Initializer.LOGGER.error("Can't obtain Available Memory info!");
                return "Available Memory: Unavailable";
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
                 return "Available Memory: " + String.format("%.2f", memFreeMB) + " MB / "  + colorPerc + freeMemoryPercentage + "%";
            } else {
                return "Available Memory: Ran Out of Available Memory";
            }
        }
        return "Available Memory: Unavailable";
    }

    public static String getBuffersInfo() {
        if (isRunningOnAndroid() && Initializer.CONFIG.showAndroidRAM) {
            long memBuffer = 0;

            try (BufferedReader br = new BufferedReader(new FileReader("\u002F\u0070\u0072\u006F\u0063\u002F\u006D\u0065\u006D\u0069\u006E\u0066\u006F"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("\u0042\u0075\u0066\u0066\u0065\u0072\u0073")) {
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
        return System.getenv("\u0050\u004F\u004A\u0041\u0056\u005F\u0052\u0045\u004E\u0044\u0045\u0052") != null;
    }
}
