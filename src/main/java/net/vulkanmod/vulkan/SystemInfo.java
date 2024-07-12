package net.vulkanmod.vulkan;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.device.AndroidDeviceChecker;
import oshi.hardware.CentralProcessor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class SystemInfo {
    public static final String cpuInfo;

    static {
        cpuInfo = AndroidDeviceChecker.isRunningOnAndroid() ? getProcessorNameForAndroid() : getProcessorNameForDesktop();
    }

    public static String getProcessorNameForAndroid() {
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

    public static String getProcessorNameForDesktop() {
        CentralProcessor centralProcessor = new oshi.SystemInfo().getHardware().getProcessor();
        return centralProcessor.getProcessorIdentifier().getName().replaceAll("\\s+", " ");
    }
}
