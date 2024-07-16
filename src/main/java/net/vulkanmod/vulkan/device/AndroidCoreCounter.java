package net.vulkanmod.vulkan.device;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class AndroidCoreCounter {

    public static final String cpuCoreCount;

    static {
        cpuCoreCount = getCpuCoreCountForAndroid() + "x ";
    }

    
    public static String getCpuCoreCountForAndroid() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            return String.valueOf(br.lines()
                    .filter(line -> line.startsWith("processor"))
                    .count());
        } catch (IOException e) {
            Initializer.LOGGER.error("Can't obtain CPU core count from /proc/cpuinfo!", e);
            return "";
        }
    }
}
