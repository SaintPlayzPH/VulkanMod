package net.vulkanmod.vulkan.queue;

import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.*;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public enum Queue {
    GraphicsQueue(QueueFamilyIndices.graphicsFamily, true, 0),
    FakeTransferQueue(QueueFamilyIndices.graphicsFamily, true, 0),
    TransferQueue(QueueFamilyIndices.transferFamily, true, 0),
    PresentQueue(QueueFamilyIndices.presentFamily, false, 0);
    private CommandPool.CommandBuffer currentCmdBuffer;
    private final CommandPool commandPool;


    private final VkQueue queue;

    public CommandPool.CommandBuffer beginCommands() {
        return this.commandPool.beginCommands();
    }

    Queue(int familyIndex) {
        this(familyIndex, true, 0);
    }

    Queue(int familyIndex, boolean initCommandPool, int queueIndex) {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(DeviceManager.vkDevice, familyIndex, queueIndex, pQueue);
            this.queue = new VkQueue(pQueue.get(0), DeviceManager.vkDevice);

            this.commandPool = initCommandPool ? new CommandPool(familyIndex) : null;
        }
    }

    public synchronized long submitCommands(CommandPool.CommandBuffer commandBuffer) {
        return this.commandPool.submitCommands(commandBuffer, queue);
    }

    public VkQueue queue() { return this.queue; }

    public void cleanUp() {
        if(commandPool != null)
            commandPool.cleanUp();
    }

    public void waitIdle() {
        vkQueueWaitIdle(queue);
    }

    public long copyBufferCmd(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try (MemoryStack stack = stackPush()) {

            CommandPool.CommandBuffer commandBuffer = beginCommands();

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack);
            copyRegion.size(size);
            copyRegion.srcOffset(srcOffset);
            copyRegion.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegion);

            this.submitCommands(commandBuffer);
            Synchronization.INSTANCE.addCommandBuffer(commandBuffer);

            return commandBuffer.fence;
        }
    }

    public void uploadBufferImmediate(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try (MemoryStack stack = stackPush()) {
            CommandPool.CommandBuffer commandBuffer = this.beginCommands();

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack);
            copyRegion.size(size);
            copyRegion.srcOffset(srcOffset);
            copyRegion.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegion);

            this.submitCommands(commandBuffer);
            vkWaitForFences(Vulkan.getVkDevice(), commandBuffer.fence, true, VUtil.UINT64_MAX);
            commandBuffer.reset();
        }
    }

    public void uploadBufferCmd(VkCommandBuffer commandBuffer, long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try (MemoryStack stack = stackPush()) {

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack);
            copyRegion.size(size);
            copyRegion.srcOffset(srcOffset);
            copyRegion.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion);
        }
    }

    public void uploadBufferCmds(CommandPool.CommandBuffer commandBuffer, long srcBuffer, long dstBuffer, VkBufferCopy.Buffer vkBufferCopies) {
        vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, vkBufferCopies);
    }

    public void startRecording() {
        currentCmdBuffer = beginCommands();
    }

    public void endRecordingAndSubmit() {
        long fence = submitCommands(currentCmdBuffer);
        Synchronization.INSTANCE.addCommandBuffer(currentCmdBuffer);

        currentCmdBuffer = null;
    }

    public CommandPool.CommandBuffer getCommandBuffer() {
        if (currentCmdBuffer != null) {
            return currentCmdBuffer;
        } else {
            return beginCommands();
        }
    }

    public long endIfNeeded(CommandPool.CommandBuffer commandBuffer) {
        if (currentCmdBuffer != null) {
            return VK_NULL_HANDLE;
        } else {
            return submitCommands(commandBuffer);
        }
    }

    public void trimCmdPool()
    {
        if(commandPool==null) return;
        VK11.vkTrimCommandPool(Vulkan.getVkDevice(), this.commandPool.id, 0);
    }
    public void fillBuffer(long id, int bufferSize, int qNaN) {

        vkCmdFillBuffer(this.getCommandBuffer().getHandle(), id, 0, bufferSize, qNaN);
    }

    public void addWriteBarrier(CommandPool.CommandBuffer commandBuffer, MemoryStack stack) {

        VkMemoryBarrier.Buffer memBarrier = VkMemoryBarrier.calloc(1, stack)
                .sType$Default()
                .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);

        vkCmdPipelineBarrier(commandBuffer.getHandle(),
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                0,
                memBarrier,
                null,
                null);
    }
    public void GigaBarrier(CommandPool.CommandBuffer commandBuffer, MemoryStack stack, boolean resize) {

        VkMemoryBarrier.Buffer memBarrier = VkMemoryBarrier.calloc(2, stack);

        //Fix WaW on SYNC_COPY_TRANSFER_WRITE
        memBarrier.get(0).sType$Default()
                //Wait on Writes depending on other Writes from prior CmdBuffers
                .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
        //Fix RaW on SYNC_VERTEX_ATTRIBUTE_INPUT_VERTEX_ATTRIBUTE_READ or SYNC_INDEX_INPUT_INDEX_READ (if Vertex or Index Buffer respectively)
        memBarrier.get(1).sType$Default()
                //Wait on Index/Vertex Attributes depending on Prior Writes from prior CmdBuffers
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_INDEX_READ_BIT | VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT);

        //When resizing only wait on this cmdBuffer's Writes

        //When Not resizing, Wait on prior Writes Depending on these Writes
        // + wait on terrain Shader Vertex+Index reads depending on last CmdBuffer's Writes
        vkCmdPipelineBarrier(
                commandBuffer.getHandle(),
                VK_PIPELINE_STAGE_TRANSFER_BIT/*resize ? VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT : VK_PIPELINE_STAGE_TRANSFER_BIT*/,
                VK_PIPELINE_STAGE_TRANSFER_BIT | VK_PIPELINE_STAGE_VERTEX_INPUT_BIT,
                0,
                memBarrier,
                null,
                null);
    }

    public void UploadCmdWriteBarrier(VkCommandBuffer handle, MemoryStack stack, boolean resize) {

        VkMemoryBarrier.Buffer memBarrier = VkMemoryBarrier.calloc(2, stack);


        memBarrier.get(0).sType$Default()

                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT) //Dependency on prior Read/Write MemBarrier
                .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT);

        memBarrier.get(1).sType$Default()

                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_INDEX_READ_BIT|VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT);


        vkCmdPipelineBarrier(
                handle,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_VERTEX_INPUT_BIT | (VK_PIPELINE_STAGE_TRANSFER_BIT),
                0,
                memBarrier,
                null,
                null);

    }
    public void PriorWriteBarrier(VkCommandBuffer commandBuffer) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier.Buffer memBarrier = VkMemoryBarrier.calloc(1, stack);

            memBarrier.sType$Default()
                    .srcAccessMask(VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);



            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_VERTEX_INPUT_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0,
                    memBarrier,
                    null,
                    null);
        }
    }
    public void GigaBarrier(VkCommandBuffer commandBuffer) {

        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier.Buffer memBarrier = VkMemoryBarrier.calloc(1, stack);

            memBarrier.sType$Default()
                    .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT|VK_ACCESS_MEMORY_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT|VK_ACCESS_MEMORY_WRITE_BIT);



            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    0,
                    memBarrier,
                    null,
                    null);
        }
    }
}

          
