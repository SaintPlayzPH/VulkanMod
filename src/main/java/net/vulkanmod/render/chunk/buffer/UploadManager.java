package net.vulkanmod.render.chunk.buffer;

import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.queue.TransferQueue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkMemoryBarrier;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class UploadManager {
    public static UploadManager INSTANCE;

    public static void createInstance() {
        INSTANCE = new UploadManager();
    }

    Queue queue = DeviceManager.getTransferQueue();
    CommandPool.CommandBuffer commandBuffer;

    public void submitUploads() {
        if (this.commandBuffer == null)
            return;

        this.queue.submitCommands(this.commandBuffer);
        Synchronization.INSTANCE.addCommandBuffer(this.commandBuffer);

        // Wait for the queue to complete execution
        Synchronization.INSTANCE.waitFences();

        this.commandBuffer = null;
    }

    public void recordUpload(Buffer buffer, long dstOffset, long bufferSize, ByteBuffer src) {
        beginCommands();

        VkCommandBuffer commandBuffer = this.commandBuffer.getHandle();

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
        stagingBuffer.copyBuffer((int) bufferSize, src);

        TransferQueue.uploadBufferCmd(commandBuffer, stagingBuffer.getId(), stagingBuffer.getOffset(), buffer.getId(), dstOffset, bufferSize);

        // Ensure memory barriers if necessary
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferMemoryBarrier.Buffer bufferMemoryBarriers = VkBufferMemoryBarrier.calloc(1, stack);
            VkBufferMemoryBarrier bufferMemoryBarrier = bufferMemoryBarriers.get(0);
            bufferMemoryBarrier.sType$Default();
            bufferMemoryBarrier.buffer(buffer.getId());
            bufferMemoryBarrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            bufferMemoryBarrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
            bufferMemoryBarrier.size(bufferSize);

            vkCmdPipelineBarrier(commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    bufferMemoryBarriers,
                    null);
        }
    }

    public void copyBuffer(Buffer src, Buffer dst) {
        copyBuffer(src, 0, dst, 0, src.getBufferSize());
    }

    public void copyBuffer(Buffer src, int srcOffset, Buffer dst, int dstOffset, int size) {
        beginCommands();

        VkCommandBuffer commandBuffer = this.commandBuffer.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferMemoryBarrier.Buffer bufferMemoryBarriers = VkBufferMemoryBarrier.calloc(2, stack);

            VkBufferMemoryBarrier srcBarrier = bufferMemoryBarriers.get(0);
            srcBarrier.sType$Default();
            srcBarrier.buffer(src.getId());
            srcBarrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            srcBarrier.dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
            srcBarrier.offset(srcOffset);
            srcBarrier.size(size);

            VkBufferMemoryBarrier dstBarrier = bufferMemoryBarriers.get(1);
            dstBarrier.sType$Default();
            dstBarrier.buffer(dst.getId());
            dstBarrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            dstBarrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT);
            dstBarrier.offset(dstOffset);
            dstBarrier.size(size);

            vkCmdPipelineBarrier(commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0,
                    null,
                    bufferMemoryBarriers,
                    null);
        }

        TransferQueue.uploadBufferCmd(commandBuffer, src.getId(), srcOffset, dst.getId(), dstOffset, size);
    }

    public void syncUploads() {
        submitUploads();

        Synchronization.INSTANCE.waitFences();
    }

    private void beginCommands() {
        if (this.commandBuffer == null) {
            this.commandBuffer = queue.beginCommands();
        }
    }
}
