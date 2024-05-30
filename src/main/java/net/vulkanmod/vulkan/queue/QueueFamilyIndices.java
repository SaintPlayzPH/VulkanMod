package net.vulkanmod.vulkan.queue;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.IntBuffer;
import java.util.stream.IntStream;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class QueueFamilyIndices {
    private static final int INVALID_QUEUE_FAMILY = VK_QUEUE_FAMILY_IGNORED;

    public static int graphicsFamily = INVALID_QUEUE_FAMILY;
    public static int presentFamily = INVALID_QUEUE_FAMILY;
    public static int transferFamily = INVALID_QUEUE_FAMILY;

    public static boolean hasDedicatedTransferQueue = false;

    public static boolean findQueueFamilies(VkPhysicalDevice device) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer queueFamilyCount = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);
            int count = queueFamilyCount.get(0);

            if (count == 1) {
                graphicsFamily = presentFamily = transferFamily = 0;
                return true;
            }

            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.mallocStack(count, stack);
            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);

            for (int i = 0; i < count; i++) {
                int queueFlags = queueFamilies.get(i).queueFlags();

                if ((queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    graphicsFamily = i;
                    if ((queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                        presentFamily = i;
                    }
                }
                if ((queueFlags & (VK_QUEUE_COMPUTE_BIT | VK_QUEUE_GRAPHICS_BIT)) == 0
                        && (queueFlags & VK_QUEUE_TRANSFER_BIT) != 0) {
                    transferFamily = i;
                }

                if (presentFamily == INVALID_QUEUE_FAMILY && (queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                    presentFamily = i;
                }

                if (isComplete()) break;
            }

            if (transferFamily == INVALID_QUEUE_FAMILY) {
                transferFamily = findTransferFamilyFallback(queueFamilies);
            }

            hasDedicatedTransferQueue = (graphicsFamily != transferFamily);

            validateQueueFamilies();

            return isComplete();
        }
    }

    private static int findTransferFamilyFallback(VkQueueFamilyProperties.Buffer queueFamilies) {
        int fallback = INVALID_QUEUE_FAMILY;
        for (int i = 0; i < queueFamilies.capacity(); i++) {
            int queueFlags = queueFamilies.get(i).queueFlags();
            if ((queueFlags & VK_QUEUE_TRANSFER_BIT) != 0) {
                if (fallback == INVALID_QUEUE_FAMILY || (queueFlags & VK_QUEUE_GRAPHICS_BIT) == 0) {
                    fallback = i;
                }
            }
        }

        if (fallback == INVALID_QUEUE_FAMILY) {
            throw new RuntimeException("Failed to find queue family with transfer support.");
        }
        return fallback;
    }

    private static void validateQueueFamilies() {
        if (graphicsFamily == INVALID_QUEUE_FAMILY) {
            throw new RuntimeException("Unable to find queue family with graphics support.");
        }
        if (presentFamily == INVALID_QUEUE_FAMILY) {
            throw new RuntimeException("Unable to find queue family with present support.");
        }
    }

    public static boolean isComplete() {
        return graphicsFamily != INVALID_QUEUE_FAMILY && presentFamily != INVALID_QUEUE_FAMILY && transferFamily != INVALID_QUEUE_FAMILY;
    }

    public static boolean isSuitable() {
        return graphicsFamily != INVALID_QUEUE_FAMILY && presentFamily != INVALID_QUEUE_FAMILY;
    }

    public static int[] unique() {
        return IntStream.of(graphicsFamily, presentFamily, transferFamily).distinct().toArray();
    }

    public static int[] array() {
        return new int[]{graphicsFamily, presentFamily};
    }
}
