package net.vulkanmod.vulkan;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.device.AndroidDeviceChecker;
import oshi.hardware.CentralProcessor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;

public class SystemInfo {
    public static final String cpuInfo;

    static {
        cpuInfo = AndroidDeviceChecker.isRunningOnAndroid() ? getProcessorNameForAndroid() : getProcessorNameForDesktop();
    }

    public static String getProcessorNameForAndroid() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            // Attempt to find processor name from "Hardware" field
            String processorName = br.lines()
                    .map(String::trim)
                    .filter(line -> line.startsWith("Hardware"))
                    .map(line -> {
                        String[] parts = line.split(":\\s+", 2);
                        if (parts.length == 2) {
                            return parts[1].trim();
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseGet(() -> {
                        // If "Hardware" field doesn't work, try "model name" field
                        try (BufferedReader innerBr = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
                            return innerBr.lines()
                                    .map(String::trim)
                                    .filter(line -> line.startsWith("model name"))
                                    .map(line -> {
                                        String[] parts = line.split(":\\s+", 2);
                                        if (parts.length == 2) {
                                            return parts[1].trim();
                                        }
                                        return null;
                                    })
                                    .filter(Objects::nonNull)
                                    .findFirst()
                                    .orElse("Unknown");
                        } catch (IOException e) {
                            Initializer.LOGGER.error("Error reading /proc/cpuinfo for model name", e);
                            return "Unknown";
                        }
                    });

            if ("Unknown".equals(processorName)) {
                Initializer.LOGGER.warn("Unable to determine SoC name.");
            }

            return processorName;
        } catch (IOException e) {
            Initializer.LOGGER.error("Error reading /proc/cpuinfo.", e);
            return "Unknown";
        }
    }

    public static String getProcessorNameForDesktop() {
        CentralProcessor centralProcessor = new oshi.SystemInfo().getHardware().getProcessor();
        return centralProcessor.getProcessorIdentifier().getName().replaceAll("\\s+", " ");
    }
}
