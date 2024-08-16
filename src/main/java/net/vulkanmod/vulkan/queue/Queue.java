package net.vulkanmod.vulkan.queue;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.IntBuffer;
import java.util.stream.IntStream;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.VK10.*;

public abstract class Queue {
    public static boolean transferFallback = false;
    public static boolean computeFallback = false;
    public static boolean presentFallback = false;
    public static boolean graphicsSupported = false;
    private static VkDevice DEVICE;

    private static QueueFamilyIndices queueFamilyIndices;
    protected CommandPool commandPool;

    private final VkQueue queue;

    public synchronized CommandPool.CommandBuffer beginCommands() {
        return this.commandPool.beginCommands();
    }

    Queue(MemoryStack stack, int familyIndex) {
        this(stack, familyIndex, true);
    }

    Queue(MemoryStack stack, int familyIndex, boolean initCommandPool) {
        PointerBuffer pQueue = stack.mallocPointer(1);
        vkGetDeviceQueue(DeviceManager.vkDevice, familyIndex, 0, pQueue);
        this.queue = new VkQueue(pQueue.get(0), DeviceManager.vkDevice);

        if (initCommandPool)
            this.commandPool = new CommandPool(familyIndex);
    }

    public synchronized long submitCommands(CommandPool.CommandBuffer commandBuffer) {
        return this.commandPool.submitCommands(commandBuffer, queue);
    }

    public VkQueue queue() {
        return this.queue;
    }

    public void cleanUp() {
        if (commandPool != null)
            commandPool.cleanUp();
    }

    public void waitIdle() {
        vkQueueWaitIdle(queue);
    }

    public enum Family {
        Graphics,
        Transfer,
        Compute
    }

    public static QueueFamilyIndices getQueueFamilies() {
        if (DEVICE == null)
            DEVICE = Vulkan.getVkDevice();

        if (queueFamilyIndices == null) {
            queueFamilyIndices = findQueueFamilies(DEVICE.getPhysicalDevice());
        }
        return queueFamilyIndices;
    }

    public static QueueFamilyIndices findQueueFamilies(VkPhysicalDevice device) {

        QueueFamilyIndices indices = new QueueFamilyIndices();

        try (MemoryStack stack = stackPush()) {

            IntBuffer queueFamilyCount = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);
            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.mallocStack(queueFamilyCount.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);

            IntBuffer presentSupport = stack.ints(VK_FALSE);

            for (int i = 0; i < queueFamilies.capacity(); i++) {
                int queueFlags = queueFamilies.get(i).queueFlags();

                if ((queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    indices.graphicsFamily = i;
                    graphicsSupported = true;

                    vkGetPhysicalDeviceSurfaceSupportKHR(device, i, Vulkan.getSurface(), presentSupport);
                    if (presentSupport.get(0) == VK_TRUE) {
                        indices.presentFamily = i;
                    }
                }

                if ((queueFlags & VK_QUEUE_COMPUTE_BIT) != 0 && indices.computeFamily == VK_QUEUE_FAMILY_IGNORED) {
                    indices.computeFamily = i;
                }

                if ((queueFlags & VK_QUEUE_TRANSFER_BIT) != 0 &&
                    (queueFlags & (VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT)) == 0 &&
                    indices.transferFamily == VK_QUEUE_FAMILY_IGNORED) {
                    indices.transferFamily = i;
                }

                if (indices.presentFamily == VK_QUEUE_FAMILY_IGNORED) {
                    vkGetPhysicalDeviceSurfaceSupportKHR(device, i, Vulkan.getSurface(), presentSupport);
                    if (presentSupport.get(0) == VK_TRUE) {
                        indices.presentFamily = i;
                    }
                }

                if (indices.isComplete())
                    break;
            }

            if (indices.presentFamily == VK_QUEUE_FAMILY_IGNORED) {
                presentFallback = true;
                indices.presentFamily = indices.computeFamily != VK_QUEUE_FAMILY_IGNORED ? indices.computeFamily : indices.graphicsFamily;
                Initializer.LOGGER.warn("Using compute or graphics queue as present fallback");
            }

            if (indices.transferFamily == VK_QUEUE_FAMILY_IGNORED) {
                transferFallback = true;
                indices.transferFamily = indices.computeFamily != VK_QUEUE_FAMILY_IGNORED ? indices.computeFamily : indices.graphicsFamily;
            }

            if (indices.computeFamily == VK_QUEUE_FAMILY_IGNORED) {
                computeFallback = true;
                indices.computeFamily = indices.graphicsFamily;
            }

            if (indices.graphicsFamily == VK_QUEUE_FAMILY_IGNORED)
                throw new RuntimeException("Unable to find queue family with graphics support.");
            if (indices.transferFamily == VK_QUEUE_FAMILY_IGNORED)
                throw new RuntimeException("Unable to find queue family with transfer support.");
            if (indices.presentFamily == VK_QUEUE_FAMILY_IGNORED)
                throw new RuntimeException("Unable to find queue family with present support.");
            if (indices.computeFamily == VK_QUEUE_FAMILY_IGNORED)
                throw new RuntimeException("Unable to find queue family with compute support.");

            return indices;
        }
    }

    public static class QueueFamilyIndices {
        public int graphicsFamily = VK_QUEUE_FAMILY_IGNORED;
        public int presentFamily = VK_QUEUE_FAMILY_IGNORED;
        public int transferFamily = VK_QUEUE_FAMILY_IGNORED;
        public int computeFamily = VK_QUEUE_FAMILY_IGNORED;

        public boolean isComplete() {
            return graphicsFamily != -1 && presentFamily != -1 && transferFamily != -1 && computeFamily != -1;
        }

        public boolean isSuitable() {
            return graphicsFamily != -1 && presentFamily != -1;
        }

        public int[] unique() {
            return IntStream.of(graphicsFamily, presentFamily, transferFamily, computeFamily).distinct().toArray();
        }

        public int[] array() {
            return new int[]{graphicsFamily, presentFamily};
        }
    }
}
