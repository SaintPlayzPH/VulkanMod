package net.vulkanmod.vulkan.queue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class CommandPool {
    long id;

    private final List<CommandBuffer> commandBuffers = new ObjectArrayList<>();
    private final java.util.Queue<CommandBuffer> availableCmdBuffers = new ArrayDeque<>();

    public CommandPool(int queueFamilyIndex) {
        this.createCommandPool(queueFamilyIndex);
    }

    private void createCommandPool(int familyIndex) {
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
                    vkCreateFence(Vulkan.getVkDevice(), fenceInfo, null, pFence);
                    CommandBuffer commandBuffer = new CommandBuffer(new VkCommandBuffer(pCommandBuffer.get(i), Vulkan.getVkDevice()), pFence.get(0));
                    commandBuffers.add(commandBuffer);
                    availableCmdBuffers.add(commandBuffer);
                }
            }

            CommandBuffer commandBuffer = availableCmdBuffers.poll();
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            vkBeginCommandBuffer(commandBuffer.handle, beginInfo);
            return commandBuffer;
        }
    }

    public long submitCommands(CommandBuffer commandBuffer, VkQueue queue) {
        try (MemoryStack stack = stackPush()) {
            long fence = commandBuffer.fence;
            vkEndCommandBuffer(commandBuffer.handle);
            vkResetFences(Vulkan.getVkDevice(), fence);

            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(commandBuffer.handle));

            vkQueueSubmit(queue, submitInfo, fence);
            return fence;
        }
    }

    public void addToAvailable(CommandBuffer commandBuffer) {
        this.availableCmdBuffers.add(commandBuffer);
    }

    public void cleanUp() {
        for (CommandBuffer commandBuffer : commandBuffers) {
            vkDestroyFence(Vulkan.getVkDevice(), commandBuffer.fence, null);
        }
        vkResetCommandPool(Vulkan.getVkDevice(), id, VK_COMMAND_POOL_RESET_RELEASE_RESOURCES_BIT);
        vkDestroyCommandPool(Vulkan.getVkDevice(), id, null);
    }

    public class CommandBuffer {
        VkCommandBuffer handle;
        long fence;
        boolean submitted;
        boolean recording;

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
            this.submitted = false;
            this.recording = false;
            addToAvailable(this);
        }
    }
}
