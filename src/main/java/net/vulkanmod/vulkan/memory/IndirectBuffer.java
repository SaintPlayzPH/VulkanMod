package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;

public class IndirectBuffer extends Buffer {
    private CommandPool.CommandBuffer commandBuffer;

    public IndirectBuffer(int size, MemoryType type) {
        super(VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT, type);
        this.createBuffer(size);
    }

    public void recordCopyCmd(ByteBuffer byteBuffer) {
        int size = byteBuffer.remaining();

        if (size > this.bufferSize - this.usedBytes) {
            resizeBuffer(size);
        }

        if (this.type.mappable()) {
            this.type.copyToBuffer(this, size, byteBuffer);
        } else {
            ensureCommandBufferInitialized();
            StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
            stagingBuffer.copyBuffer(size, byteBuffer);
            Queue.TransferQueue.uploadBufferCmd(commandBuffer.getHandle(), stagingBuffer.id, stagingBuffer.offset, this.getId(), this.getUsedBytes(), size);
        }

        this.usedBytes += size;
    }

    private void ensureCommandBufferInitialized() {
        if (commandBuffer == null) {
            commandBuffer = DeviceManager.getTransferQueue().beginCommands();
        }
    }

    private void resizeBuffer(int requiredSize) {
        int newSize = Math.max(this.bufferSize + (this.bufferSize >> 1), requiredSize);
        MemoryManager.getInstance().addToFreeable(this);
        this.createBuffer(newSize);
        this.usedBytes = 0;
    }

    public void submitUploads() {
        if (commandBuffer != null) {
            Queue.TransferQueue.submitCommands(commandBuffer);
            Synchronization.INSTANCE.addCommandBuffer(commandBuffer);
            commandBuffer = null;
        }
    }

    // Debug method to get ByteBuffer
    public ByteBuffer getByteBuffer() {
        return this.data.getByteBuffer(0, this.bufferSize);
    }
}
