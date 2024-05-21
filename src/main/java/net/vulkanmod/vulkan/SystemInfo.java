package net.vulkanmod.vulkan;

import net.vulkanmod.Initializer;
import oshi.hardware.CentralProcessor;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class SystemInfo {
    private static boolean procObtain = false;
    private static boolean cpuObtain = false;
    public static final String cpuInfo;

    static {
        if (isRunningOnAndroid()) {
            if (!procObtain) {
                Initializer.LOGGER.info("Obtaining Processor Name on your Device since you're running on Mobile!");
                procObtain = true;
            }
            cpuInfo = getProcessorName();
        } else {
            if (!cpuObtain) {
                Initializer.LOGGER.info("Obtaining CPU Name on your Device!");
                cpuObtain = true;
            }
            CentralProcessor centralProcessor = new oshi.SystemInfo().getHardware().getProcessor();
            cpuInfo = String.format("%s", centralProcessor.getProcessorIdentifier().getName()).replaceAll("\\s+", " ");
        }
    }

    private static String getProcessorName() {
        if (isRunningOnAndroid()) {
            boolean errorLogged = false;

            try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("Hardware")) {
                        return line.split(":\\s+", 2)[1];
                    }
                }
            } catch (IOException e) {
                if (!errorLogged) {
                    String cpuInfo = "Unknown";
                    Initializer.LOGGER.error("Can't read your Mobile processor. Setting to Unknown!");
                    errorLogged = true;
                }
            }
        }
        return "Unknown";
    }

    private static boolean isRunningOnAndroid() {
        if (System.getenv("POJAV_RENDERER") != null) {
            return true;
        } else {
            return false;
        }
    }
}
