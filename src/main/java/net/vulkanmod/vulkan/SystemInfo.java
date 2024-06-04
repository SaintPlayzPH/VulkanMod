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
        boolean isAndroid = isRunningOnAndroid();
        if (isAndroid) {
            if (!procObtain) {
                Initializer.LOGGER.info("Obtaining Processor Name on your Device since you're running on Mobile!");
                procObtain = true;
            }
            cpuInfo = getProcessorNameForAndroid();
        } else {
            if (!cpuObtain) {
                Initializer.LOGGER.info("Obtaining CPU Name on your Device!");
                cpuObtain = true;
            }
            CentralProcessor centralProcessor = new oshi.SystemInfo().getHardware().getProcessor();
            cpuInfo = centralProcessor.getProcessorIdentifier().getName().replaceAll("\\s+", " ");
        }
    }

    public static String getProcessorNameForAndroid() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("Hardware")) {
                    return line.split(":\\s+", 2)[1];
                }
            }
        } catch (IOException e) {
            Initializer.LOGGER.error("Can't obtain your Mobile processor name!");
        }
        return "Unknown";
    }

    private static boolean isRunningOnAndroid() {
        if (System.getenv("POJAV_ENVIRON") != null) { //PojavLauncher
            return true;
        }
        if (System.getenv("SCL_ENVIRON") != null) { //SolCraftLauncher
            return true;
        }
        return System.getenv("POJAV_RENDERER") != null;
    }
}
