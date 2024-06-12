package net.vulkanmod.vulkan.memory;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkMemoryType;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.vulkan.VK10.*;

public enum MemoryType {
    GPU_MEM(true, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT),

    BAR_MEM(true, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT),

    RAM_MEM(false, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, 0);

    private final long maxSize;
    private final AtomicLong usedBytes = new AtomicLong(0);
    private final int flags;

    MemoryType(boolean useVRAM, int... optimalFlags) {
        final boolean useVRAM1 = useVRAM || !hasHeapFlag(0);

        for (int optimalFlagMask : optimalFlags) {
            final String requiredFlagString = getMemoryTypeFlags(optimalFlagMask);
            Initializer.LOGGER.info("Requesting Flags: " + requiredFlagString + "...");

            for (VkMemoryType memoryType : DeviceManager.memoryProperties.memoryTypes()) {
                VkMemoryHeap memoryHeap = DeviceManager.memoryProperties.memoryHeaps(memoryType.heapIndex());
                final int availableFlags = memoryType.propertyFlags();
                final int extractedFlags = optimalFlagMask & availableFlags;
                final boolean hasRequiredFlags = extractedFlags == optimalFlagMask;

                if (availableFlags != 0) {
                    Initializer.LOGGER.info("AvailableFlags: " + getMemoryTypeFlags(availableFlags) + " --> " + "SelectedFlags: " + getMemoryTypeFlags(extractedFlags));
                }

                final boolean hasMemType = useVRAM1 == ((availableFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0);
                if (hasRequiredFlags && hasMemType) {
                    this.maxSize = memoryHeap.size();
                    this.flags = optimalFlagMask;

                    Initializer.LOGGER.info("Found Requested Flags for: " + this.name() + "\n"
                            + "     Memory Heap Index/Bank: " + memoryType.heapIndex() + "\n"
                            + "     IsVRAM: " + memoryHeap.flags() + "\n"
                            + "     MaxSize: " + this.maxSize + " Bytes" + "\n"
                            + "     AvailableFlags:" + getMemoryTypeFlags(availableFlags) + "\n"
                            + "     EnabledFlags:" + requiredFlagString);

                    return;
                }
            }
            Initializer.LOGGER.error(requiredFlagString + " Not Found, using next Fallback");
        }

        throw new RuntimeException("Unsupported MemoryType: " + this.name() + ": Try updating your driver and/or Vulkan version");
    }

    private String getMemoryTypeFlags(int memFlags) {
        if (memFlags == 0) return " | NONE/SKIPPING";
        StringBuilder memTypeFlags = new StringBuilder();
        if ((memFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0) memTypeFlags.append(" | DEVICE_LOCAL");
        if ((memFlags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0) memTypeFlags.append(" | HOST_VISIBLE");
        if ((memFlags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0) memTypeFlags.append(" | HOST_COHERENT");
        if ((memFlags & VK_MEMORY_PROPERTY_HOST_CACHED_BIT) != 0) memTypeFlags.append(" | HOST_CACHED");
        if ((memFlags & VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT) != 0) memTypeFlags.append(" | LAZILY_ALLOCATED");
        return memTypeFlags.toString();
    }

    private static boolean hasHeapFlag(int heapFlag) {
        for (VkMemoryHeap memoryHeap : DeviceManager.memoryProperties.memoryHeaps()) {
            if ((memoryHeap.flags() & heapFlag) != 0) return true;
        }
        return false;
    }

    void createBuffer(Buffer buffer, int size) {
        final int usage = buffer.usage | (this.mappable() ? 0 : VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
        MemoryManager.getInstance().createBuffer(buffer, size, usage, this.flags);
        this.usedBytes.addAndGet(size);
    }

    void copyToBuffer(Buffer buffer, int bufferSize, ByteBuffer byteBuffer) {
        if (!this.mappable()) {
            StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
            stagingBuffer.copyBuffer(bufferSize, byteBuffer);
            DeviceManager.getTransferQueue().copyBufferCmd(stagingBuffer.id, stagingBuffer.offset, buffer.getId(), buffer.getUsedBytes(), bufferSize);
        } else {
            VUtil.memcpy(byteBuffer, buffer.data.getByteBuffer(0, buffer.bufferSize), bufferSize, buffer.getUsedBytes());
        }
    }

    void freeBuffer(Buffer buffer) {
        MemoryManager.getInstance().addToFreeable(buffer);
        this.usedBytes.addAndGet(-buffer.bufferSize);
    }

    public void uploadBuffer(Buffer buffer, ByteBuffer byteBuffer, int dstOffset) {
        if (!this.mappable()) {
            int bufferSize = byteBuffer.remaining();
            StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
            stagingBuffer.copyBuffer(bufferSize, byteBuffer);
            DeviceManager.getTransferQueue().copyBufferCmd(stagingBuffer.id, stagingBuffer.offset, buffer.getId(), dstOffset, bufferSize);
        } else {
            VUtil.memcpy(byteBuffer, buffer.data.getByteBuffer(0, buffer.bufferSize), byteBuffer.remaining(), dstOffset);
        }
    }

    final boolean mappable() {
        return (this.flags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0;
    }

    public int usedBytes() {
        return (int) (this.usedBytes.get() >> 20);
    }

    public int maxSize() {
        return (int) (this.maxSize >> 20);
    }
}
