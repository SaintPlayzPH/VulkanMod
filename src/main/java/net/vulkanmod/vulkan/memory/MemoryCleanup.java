package net.vulkanmod.vulkan.memory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.vulkanmod.Initializer;

public class MemoryCleanup {
    private static final int CLEANUP_INTERVAL_MINUTES = 1;
    private static final Lock lock = new ReentrantLock();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    static {
        scheduler.scheduleAtFixedRate(MemoryCleanup::cleanUpMemory, 0, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private static void cleanUpMemory() {
        lock.lock();
        try {
            System.gc();
            Initializer.LOGGER.info("Memory cleaned up!");
        } finally {
            lock.unlock();
        }
    }
}
