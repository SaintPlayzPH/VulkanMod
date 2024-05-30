package net.vulkanmod.vulkan.queue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class CommandPool {
    public long id;
    public final List<CommandBuffer> commandBuffers = new ObjectArrayList<>();
    public final Queue<CommandBuffer> availableCmdBuffers = new ArrayDeque<>();

    public CommandPool(int queueFamilyIndex) {
        createCommandPool(queueFamilyIndex);
    }

    public void createCommandPool(int familyIndex) {
        try (MemoryStack stack = stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .queueFamilyIndex(familyIndex)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            LongBuffer pCommandPool = stack.mallocLong(1);
            if (vkCreateCommandPool(Vulkan.getVkDevice(), poolInfo, null, pCommandPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool");
            }
            this.id = pCommandPool.get(0);
        }
    }

    public CommandBuffer beginCommands() {
        try (MemoryStack stack = stackPush()) {
            final int size = 10;
            if (availableCmdBuffers.isEmpty()) {
                VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                        .sType$Default()
                        .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                        .commandPool(id)
                        .commandBufferCount(size);

                PointerBuffer pCommandBuffer = stack.mallocPointer(size);
                vkAllocateCommandBuffers(Vulkan.getVkDevice(), allocInfo, pCommandBuffer);

                VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                        .sType$Default()
                        .flags(VK_FENCE_CREATE_SIGNALED_BIT);

                for (int i = 0; i < size; ++i) {
                    LongBuffer pFence = stack.mallocLong(1);
                    if (vkCreateFence(Vulkan.getVkDevice(), fenceInfo, null, pFence) != VK_SUCCESS) {
                        throw new RuntimeException("Failed to create fence");
                    }
                    CommandBuffer commandBuffer = new CommandBuffer(new VkCommandBuffer(pCommandBuffer.get(i), Vulkan.getVkDevice()), pFence.get(0));
                    commandBuffers.add(commandBuffer);
                    availableCmdBuffers.add(commandBuffer);
                }
            }

            CommandBuffer commandBuffer = availableCmdBuffers.poll();
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            if (vkBeginCommandBuffer(commandBuffer.handle, beginInfo) != VK_SUCCESS) {
                throw new RuntimeException("Failed to begin recording command buffer");
            }
            commandBuffer.recording = true;
            return commandBuffer;
        }
    }

    public long submitCommands(CommandBuffer commandBuffer, VkQueue queue) {
        try (MemoryStack stack = stackPush()) {
            long fence = commandBuffer.fence;
            if (vkEndCommandBuffer(commandBuffer.handle) != VK_SUCCESS) {
                throw new RuntimeException("Failed to end recording command buffer");
            }
            vkResetFences(Vulkan.getVkDevice(), fence);

            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(commandBuffer.handle));

            if (vkQueueSubmit(queue, submitInfo, fence) != VK_SUCCESS) {
                throw new RuntimeException("Failed to submit command buffer");
            }
            commandBuffer.submitted = true;
            return fence;
        }
    }

    public void addToAvailable(CommandBuffer commandBuffer) {
        commandBuffer.reset();
        availableCmdBuffers.add(commandBuffer);
    }

    public void cleanUp() {
        for (CommandBuffer commandBuffer : commandBuffers) {
            vkDestroyFence(Vulkan.getVkDevice(), commandBuffer.fence, null);
        }
        vkDestroyCommandPool(Vulkan.getVkDevice(), id, null);
    }

    public class CommandBuffer {
        public final VkCommandBuffer handle;
        public final long fence;
        public boolean submitted;
        public boolean recording;

        public CommandBuffer(VkCommandBuffer handle, long fence) {
            this.handle = handle;
            this.fence = fence;
        }

        public VkCommandBuffer getHandle() {
            return handle;
        }

        public long getFence() {
            return fence;
        }

        public boolean isSubmitted() {
            return submitted;
        }

        public boolean isRecording() {
            return recording;
        }

        public void reset() {
            if (submitted) {
                vkWaitForFences(Vulkan.getVkDevice(), fence, true, VUtil.UINT64_MAX);
                vkResetCommandBuffer(handle, 0);
                submitted = false;
            }
            recording = false;
        }
    }
}
