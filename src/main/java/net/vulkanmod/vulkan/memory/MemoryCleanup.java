package net.vulkanmod.vulkan.memory;

import java.util.Timer;
import java.util.TimerTask;
import net.vulkanmod.Initializer;

public class MemoryCleanup {
    public static void main(String[] args) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.gc();
                Initializer.LOGGER.info("Memory Cleaned Up!");
            }
        }, 0, 30000);
    }
}
