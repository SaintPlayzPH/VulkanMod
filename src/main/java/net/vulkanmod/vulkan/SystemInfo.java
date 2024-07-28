package net.vulkanmod.vulkan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class SystemInfo {
    public static final String cpuInfo;

    static {
        cpuInfo = isRunningOnAndroid() ? getProcessorNameForAndroid() : getProcessorNameForDesktop();
    }

    public static String getProcessorNameForAndroid() {
        try (Stream<String> lines = Files.lines(Paths.get("/proc/cpuinfo"))) {
            return lines.filter(line -> line.startsWith("Hardware") || line.startsWith("model name"))
                    .reduce((f, s) -> f.startsWith("H") ? f : s)
                    .map(line -> {
                        String value = line.split(":")[1].trim();
                        return line.startsWith("H") ? value.split("/")[0] + " (SoC)" : value;
                    }).orElse("Unknown SoC");
        } catch (IOException e) {
            return "Unknown SoC";
        }
    }

    public static String getProcessorNameForDesktop() {
        try {
            return new oshi.SystemInfo().getHardware().getProcessor().getProcessorIdentifier().getName().replaceAll("\\s+", " ");
        } catch (Exception e) {
            return "Unknown CPU";
        }
    }

    private static boolean isRunningOnAndroid() {
        String osName = System.getProperty("os.name").toLowerCase();
        return (osName.contains("linux") || osName.contains("android")) &&
                (System.getenv("POJAV_ENVIRON") != null ||
                        System.getenv("SCL_ENVIRON") != null ||
                        System.getenv("SCL_RENDERER") != null ||
                        System.getenv("POJAV_RENDERER") != null);
    }
}
