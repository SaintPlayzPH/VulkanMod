package net.vulkanmod.render.chunk.buffer;

import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkMemoryBarrier;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.*;

public class UploadManager {
    private static final UploadManager INSTANCE = new UploadManager();

    private final Queue queue = DeviceManager.getTransferQueue();
    private CommandPool.CommandBuffer commandBuffer;
    private final Set<Long> dstBuffers = new HashSet<>();

    private UploadManager() {
        // Private constructor to enforce singleton pattern
    }

    public static UploadManager getInstance() {
        return INSTANCE;
    }

    public void submitUploads() {
        if (this.commandBuffer != null) {
            queue.submitCommands(this.commandBuffer);
            this.commandBuffer = null;
        }
    }

    public void recordUpload(long bufferId, long dstOffset, long bufferSize, ByteBuffer src) {
        if (this.commandBuffer == null) {
            this.commandBuffer = queue.beginCommands();
            beginBarrier();
        }

        VkCommandBuffer commandBufferHandle = this.commandBuffer.getHandle();

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
        stagingBuffer.copyBuffer((int) bufferSize, src);

        if (!this.dstBuffers.add(bufferId)) {
            endBarrier(commandBufferHandle);
            beginBarrier();
            this.dstBuffers.clear();
        }

        TransferQueue.uploadBufferCmd(commandBufferHandle, stagingBuffer.getId(), stagingBuffer.getOffset(), bufferId, dstOffset, bufferSize);
    }

    public void copyBuffer(Buffer src, Buffer dst) {
        copyBuffer(src, 0, dst, 0, src.getBufferSize());
    }

    public void copyBuffer(Buffer src, int srcOffset, Buffer dst, int dstOffset, int size) {
        if (this.commandBuffer == null) {
            this.commandBuffer = queue.beginCommands();
            beginBarrier();
        }

        VkCommandBuffer commandBufferHandle = this.commandBuffer.getHandle();

        if (!this.dstBuffers.add(dst.getId())) {
            endBarrier(commandBufferHandle);
            beginBarrier();
            this.dstBuffers.clear();
            this.dstBuffers.add(dst.getId());
        }

        TransferQueue.uploadBufferCmd(commandBufferHandle, src.getId(), srcOffset, dst.getId(), dstOffset, size);
    }

    public void waitUploads() {
        if (this.commandBuffer != null) {
            Synchronization.INSTANCE.addCommandBuffer(this.commandBuffer);
            this.commandBuffer = null;
            this.dstBuffers.clear();
        }
    }

    private void beginBarrier() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1, stack);
            barrier.sType$Default();
            barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);

            vkCmdPipelineBarrier(this.commandBuffer.getHandle(),
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0,
                    barrier,
                    null,
                    null);
        }
    }

    private void endBarrier(VkCommandBuffer commandBufferHandle) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1, stack);
            barrier.sType$Default();
            barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);

            vkCmdPipelineBarrier(commandBufferHandle,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0,
                    barrier,
                    null,
                    null);
        }
    }
}
