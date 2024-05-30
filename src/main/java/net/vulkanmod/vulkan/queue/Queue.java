package net.vulkanmod.vulkan.queue;

import net.vulkanmod.vulkan.device.DeviceManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.vulkanmod.render.chunk.SubCopyCommand;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public enum Queue {
    GraphicsQueue(QueueFamilyIndices.graphicsFamily, true, 0),
    FakeTransferQueue(QueueFamilyIndices.graphicsFamily, true, 0),
    TransferQueue(QueueFamilyIndices.transferFamily, true, 0),
    PresentQueue(QueueFamilyIndices.presentFamily, false, 0);

    private final CommandPool commandPool;
    private final int familyIndex;
    private final VkQueue queue;
    private CommandPool.CommandBuffer currentCmdBuffer;

    Queue(int familyIndex, boolean initCommandPool, int queueIndex) {
        this.familyIndex = familyIndex;
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(DeviceManager.vkDevice, familyIndex, queueIndex, pQueue);
            this.queue = new VkQueue(pQueue.get(0), DeviceManager.vkDevice);
            this.commandPool = initCommandPool ? new CommandPool(familyIndex) : null;
        }
    }

    public CommandPool.CommandBuffer beginCommands() {
        return this.commandPool.beginCommands();
    }

    public long submitCommands(CommandPool.CommandBuffer commandBuffer) {
        return this.commandPool.submitCommands(commandBuffer, queue);
    }

    public VkQueue queue() {
        return this.queue;
    }

    public void cleanUp() {
        if (commandPool != null) {
            commandPool.cleanUp();
        }
    }

    public void waitIdle() {
        vkQueueWaitIdle(queue);
    }

    public long copyBufferCmd(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {
        try (MemoryStack stack = stackPush()) {
            CommandPool.CommandBuffer commandBuffer = beginCommands();
            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack)
                .srcOffset(srcOffset)
                .dstOffset(dstOffset)
                .size(size);

            vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegion);
            long fence = this.submitCommands(commandBuffer);
            Synchronization.INSTANCE.addCommandBuffer(commandBuffer);
            return fence;
        }
    }

    public void uploadBufferImmediate(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {
        try (MemoryStack stack = stackPush()) {
            CommandPool.CommandBuffer commandBuffer = beginCommands();
            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack)
                .srcOffset(srcOffset)
                .dstOffset(dstOffset)
                .size(size);

            vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegion);
            this.submitCommands(commandBuffer);
            vkWaitForFences(Vulkan.getVkDevice(), commandBuffer.fence, true, VUtil.UINT64_MAX);
            commandBuffer.reset();
        }
    }

    public void uploadBufferCmd(VkCommandBuffer commandBuffer, long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {
        try (MemoryStack stack = stackPush()) {
            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack)
                .srcOffset(srcOffset)
                .dstOffset(dstOffset)
                .size(size);

            vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion);
        }
    }

    public void uploadBufferCmds(CommandPool.CommandBuffer commandBuffer, long srcBuffer, Long2ObjectMap<ObjectArrayFIFOQueue<SubCopyCommand>> dstBuffers) {
        try (MemoryStack stack = stackPush()) {
            for (var entry : dstBuffers.long2ObjectEntrySet()) {
                ObjectArrayFIFOQueue<SubCopyCommand> subCmdUploads = entry.getValue();
                VkBufferCopy.Buffer vkBufferCopies = VkBufferCopy.malloc(subCmdUploads.size(), stack);
                for (var subCpy : vkBufferCopies) {
                    SubCopyCommand subCopyCommand = subCmdUploads.dequeue();
                    subCpy.set(subCopyCommand.srcOffset(), subCopyCommand.dstOffset(), subCopyCommand.bufferSize());
                }
                vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, entry.getLongKey(), vkBufferCopies);
            }
        }
    }

    public void startRecording() {
        currentCmdBuffer = beginCommands();
    }

    public void endRecordingAndSubmit() {
        if (currentCmdBuffer != null) {
            long fence = submitCommands(currentCmdBuffer);
            Synchronization.INSTANCE.addCommandBuffer(currentCmdBuffer);
            currentCmdBuffer = null;
        }
    }

    public CommandPool.CommandBuffer getCommandBuffer() {
        return currentCmdBuffer != null ? currentCmdBuffer : beginCommands();
    }

    public long endIfNeeded(CommandPool.CommandBuffer commandBuffer) {
        return currentCmdBuffer != null ? VK_NULL_HANDLE : submitCommands(commandBuffer);
    }

    public void trimCmdPool() {
        if (commandPool != null) {
            VK11.vkTrimCommandPool(Vulkan.getVkDevice(), this.commandPool.id, 0);
        }
    }

    public static void trimCmdPools() {
        for (var queue : Queue.values()) {
            queue.trimCmdPool();
        }
    }

    public void fillBuffer(long id, int bufferSize, int qNaN) {
        vkCmdFillBuffer(getCommandBuffer().getHandle(), id, 0, bufferSize, qNaN);
    }

    public void bufferBarrier(VkCommandBuffer commandBuffer, long bufferHandle, int size, int srcAccess, int dstAccess, int srcStage, int dstStage) {
        try (MemoryStack stack = stackPush()) {
            VkBufferMemoryBarrier.Buffer memBarrier = VkBufferMemoryBarrier.calloc(1, stack)
                .sType$Default()
                .buffer(bufferHandle)
                .srcQueueFamilyIndex(familyIndex)
                .dstQueueFamilyIndex(familyIndex)
                .srcAccessMask(srcAccess)
                .dstAccessMask(dstAccess)
                .size(size);

            vkCmdPipelineBarrier(commandBuffer, srcStage, dstStage, 0, null, memBarrier, null);
        }
    }

    public void memoryBarrier(VkCommandBuffer commandBuffer, int srcAccess, int dstAccess, int srcStage, int dstStage) {
        try (MemoryStack stack = stackPush()) {
            VkMemoryBarrier.Buffer memBarrier = VkMemoryBarrier.calloc(1, stack)
                .sType$Default()
                .srcAccessMask(srcAccess)
                .dstAccessMask(dstAccess);

            vkCmdPipelineBarrier(commandBuffer, srcStage, dstStage, 0, memBarrier, null, null);
        }
    }

    public void multiBufferBarriers(VkCommandBuffer commandBuffer, LongSet bufferHandles, int srcAccess, int dstAccess, int srcStage, int dstStage) {
        try (MemoryStack stack = stackPush()) {
            VkBufferMemoryBarrier.Buffer memBarriers = VkBufferMemoryBarrier.malloc(bufferHandles.size(), stack);
            int i = 0;
            for (var bufferHandle : bufferHandles) {
                memBarriers.get(i)
                    .sType$Default()
                    .buffer(bufferHandle)
                    .srcQueueFamilyIndex(familyIndex)
                    .dstQueueFamilyIndex(familyIndex)
                    .srcAccessMask(srcAccess)
                    .dstAccessMask(dstAccess)
                    .size(~0);
                i++;
            }
            vkCmdPipelineBarrier(commandBuffer, srcStage, dstStage, 0, null, memBarriers, null);
        }
    }

    public void gigaBarrier(VkCommandBuffer commandBuffer, int srcStage, int dstStage, boolean flushReads) {
        try (MemoryStack stack = stackPush()) {
            VkMemoryBarrier.Buffer memBarrier = VkMemoryBarrier.calloc(1, stack)
                .sType$Default()
                .srcAccessMask(flushReads ? (VK_ACCESS_MEMORY_WRITE_BIT | VK_ACCESS_MEMORY_READ_BIT) : 0)
                .dstAccessMask(flushReads ? (VK_ACCESS_MEMORY_WRITE_BIT | VK_ACCESS_MEMORY_READ_BIT) : VK_ACCESS_MEMORY_WRITE_BIT);

            vkCmdPipelineBarrier(commandBuffer, srcStage, dstStage, 0, memBarrier, null, null);
        }
    }

    public void updateBuffer(CommandPool.CommandBuffer commandBuffer, long id, int baseOffset, long bufferPtr, int size) {
        nvkCmdUpdateBuffer(commandBuffer.getHandle(), id, baseOffset, size, bufferPtr);
    }
}
