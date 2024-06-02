package net.vulkanmod.vulkan.memory;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.vulkanmod.Initializer;

public class MemoryCleanup {
    private static final int CLEANUP_INTERVAL_MS = 30 * 1000;
    private static final Lock lock = new ReentrantLock();

    static {
        Thread memoryCleanupThread = new Thread(() -> {
            while (true) {
                cleanUpMemory();
                try {
                    Thread.sleep(CLEANUP_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        memoryCleanupThread.setDaemon(true);
        memoryCleanupThread.start();
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

    public static void start() {}
}
