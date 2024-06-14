package net.vulkanmod.vulkan;

import net.vulkanmod.Initializer;
import oshi.hardware.CentralProcessor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class SystemInfo {
    public static final String cpuInfo;

    static {
        cpuInfo = isRunningOnAndroid() ? getProcessorNameForAndroid() : getProcessorNameForDesktop();
    }

    private static String getProcessorNameForAndroid() {
        Initializer.LOGGER.info("Obtaining Processor Name on your Device since you're running on Mobile!");

        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            return br.lines()
                    .filter(line -> line.startsWith("Hardware"))
                    .map(line -> line.split(":\\s+", 2)[1])
                    .findFirst()
                    .orElse("Unknown");
        } catch (IOException e) {
            Initializer.LOGGER.error("Can't obtain your Mobile processor name!", e);
            return "Unknown";
        }
    }

    private static String getProcessorNameForDesktop() {
       Initializer.LOGGER.info("Obtaining CPU Name on your Device!");

        var centralProcessor = new oshi.SystemInfo().getHardware().getProcessor();
        return centralProcessor.getProcessorIdentifier().getName().replaceAll("\\s+", " ");
    }

    private static boolean isRunningOnAndroid() {
        return System.getenv("POJAV_ENVIRON") != null ||
               System.getenv("SCL_ENVIRON") != null ||
               System.getenv("POJAV_RENDERER") != null;
    }
}
