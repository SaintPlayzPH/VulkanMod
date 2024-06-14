package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.queue.CommandPool;

import java.nio.ByteBuffer;

import static net.vulkanmod.vulkan.queue.Queue.TransferQueue;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;

public class IndirectBuffer extends Buffer {
    CommandPool.CommandBuffer commandBuffer;

    public IndirectBuffer(int size, MemoryType type) {
        super(VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT, type);
        this.createBuffer(size);
    }

    public void recordCopyCmd(ByteBuffer byteBuffer) {
        int size = byteBuffer.remaining();
        ensureCapacity(size);

        if (this.type.mappable()) {
            this.type.copyToBuffer(this, size, byteBuffer);
        } else {
            handleNonMappableCopy(size, byteBuffer);
        }

        updateUsage(size);
    }

    private void ensureCapacity(int size) {
        if (size > this.bufferSize - this.usedBytes) {
            resizeBuffer();
        }
    }

    private void handleNonMappableCopy(int size, ByteBuffer byteBuffer) {
        if (commandBuffer == null) {
            commandBuffer = DeviceManager.getTransferQueue().beginCommands();
        }

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
        stagingBuffer.copyBuffer(size, byteBuffer);

        TransferQueue.uploadBufferCmd(
            commandBuffer.getHandle(),
            stagingBuffer.getId(),
            stagingBuffer.getOffset(),
            this.getId(),
            this.getUsedBytes(),
            size
        );
    }

    private void resizeBuffer() {
        MemoryManager.getInstance().addToFreeable(this);
        int newSize = this.bufferSize + (this.bufferSize >> 1); // increase by 1.5 times
        this.createBuffer(newSize);
        this.usedBytes = 0; // reset usedBytes to 0 after resizing
    }

    private void updateUsage(int size) {
        this.offset = this.usedBytes;
        this.usedBytes += size;
    }

    public void submitUploads() {
        if (commandBuffer != null) {
            DeviceManager.getTransferQueue().submitCommands(commandBuffer);
            Synchronization.INSTANCE.addCommandBuffer(commandBuffer);
            commandBuffer = null;
        }
    }

    // debug method
    public ByteBuffer getByteBuffer() {
        return this.data.getByteBuffer(0, this.bufferSize);
    }
}
