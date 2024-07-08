package net.vulkanmod.vulkan.texture;

import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public abstract class ImageUtil {

    public static void copyBufferToImageCmd(VkCommandBuffer commandBuffer, long buffer, long image, int mipLevel, int width, int height, int xOffset, int yOffset, int bufferOffset, int bufferRowLength, int bufferImageHeight) {
        try (MemoryStack stack = stackPush()) {
            VkBufferImageCopy.Buffer region = createBufferImageCopy(stack, bufferOffset, bufferRowLength, bufferImageHeight, mipLevel, width, height, xOffset, yOffset);
            vkCmdCopyBufferToImage(commandBuffer, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
        }
    }

    public static void downloadTexture(VulkanImage image, long ptr) {
        try (MemoryStack stack = stackPush()) {
            int prevLayout = image.getCurrentLayout();
            CommandPool.CommandBuffer commandBuffer = DeviceManager.getGraphicsQueue().beginCommands();
            image.transitionImageLayout(stack, commandBuffer.getHandle(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);

            long imageSize = (long) image.width * image.height * image.formatSize;

            LongBuffer pStagingBuffer = stack.mallocLong(1);
            PointerBuffer pStagingAllocation = stack.pointers(0L);

            boolean bufferCreated = createStagingBuffer(stack, imageSize, pStagingBuffer, pStagingAllocation);
            if (!bufferCreated) {
                throw new RuntimeException("Failed to create buffer for downloading image");
            }

            copyImageToBuffer(commandBuffer.getHandle(), pStagingBuffer.get(0), image.getId(), 0, image.width, image.height, 0, 0, 0, 0, 0);
            image.transitionImageLayout(stack, commandBuffer.getHandle(), prevLayout);

            long fence = DeviceManager.getGraphicsQueue().submitCommands(commandBuffer);
            vkWaitForFences(DeviceManager.vkDevice, fence, true, VUtil.UINT64_MAX);

            MemoryManager.MapAndCopy(pStagingAllocation.get(0), (data) -> VUtil.memcpy(data.getByteBuffer(0, (int) imageSize), ptr));
            MemoryManager.freeBuffer(pStagingBuffer.get(0), pStagingAllocation.get(0));
        }
    }

    public static void copyImageToBuffer(VkCommandBuffer commandBuffer, long buffer, long image, int mipLevel, int width, int height, int xOffset, int yOffset, int bufferOffset, int bufferRowLength, int bufferImageHeight) {
        try (MemoryStack stack = stackPush()) {
            VkBufferImageCopy.Buffer region = createBufferImageCopy(stack, bufferOffset, bufferRowLength, bufferImageHeight, mipLevel, width, height, xOffset, yOffset);
            vkCmdCopyImageToBuffer(commandBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, region);
        }
    }

    public static void generateMipmaps(VulkanImage image) {
        try (MemoryStack stack = stackPush()) {
            CommandPool.CommandBuffer commandBuffer = DeviceManager.getGraphicsQueue().beginCommands();
            image.transitionImageLayout(stack, commandBuffer.getHandle(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);

            for (int level = 1; level < image.mipLevels; level++) {
                transitionMipLevel(commandBuffer.getHandle(), stack, image.getId(), level - 1, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_ACCESS_TRANSFER_READ_BIT);

                VkImageBlit.Buffer blit = createImageBlit(stack, image, level);
                vkCmdBlitImage(commandBuffer.getHandle(), image.getId(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, image.getId(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, blit, VK_FILTER_LINEAR);
            }

            transitionImageLayoutForMipmaps(stack, commandBuffer.getHandle(), image);

            long fence = DeviceManager.getGraphicsQueue().submitCommands(commandBuffer);
            vkWaitForFences(DeviceManager.vkDevice, fence, true, VUtil.UINT64_MAX);
        }
    }

    private static VkBufferImageCopy.Buffer createBufferImageCopy(MemoryStack stack, int bufferOffset, int bufferRowLength, int bufferImageHeight, int mipLevel, int width, int height, int xOffset, int yOffset) {
        VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
        region.bufferOffset(bufferOffset);
        region.bufferRowLength(bufferRowLength);
        region.bufferImageHeight(bufferImageHeight);
        region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
        region.imageSubresource().mipLevel(mipLevel);
        region.imageSubresource().baseArrayLayer(0);
        region.imageSubresource().layerCount(1);
        region.imageOffset().set(xOffset, yOffset, 0);
        region.imageExtent(VkExtent3D.calloc(stack).set(width, height, 1));
        return region;
    }

    private static boolean createStagingBuffer(MemoryStack stack, long imageSize, LongBuffer pStagingBuffer, PointerBuffer pStagingAllocation) {
        int[] memoryProperties = {
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT | VK_MEMORY_PROPERTY_HOST_CACHED_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            VK_MEMORY_PROPERTY_HOST_COHERENT_BIT | VK_MEMORY_PROPERTY_HOST_CACHED_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_CACHED_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
            VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            VK_MEMORY_PROPERTY_HOST_CACHED_BIT
        };

        for (int properties : memoryProperties) {
            try {
                MemoryManager.getInstance().createBuffer(imageSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT, properties, pStagingBuffer, pStagingAllocation);
                return true;
            } catch (Exception e) {}
        }
        return false;
    }

    private static void transitionMipLevel(VkCommandBuffer commandBuffer, MemoryStack stack, long imageId, int level, int newLayout, int newAccessMask) {
        VkImageMemoryBarrier.Buffer barrier = createImageMemoryBarrier(stack, imageId, level, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, newLayout, VK_ACCESS_TRANSFER_WRITE_BIT, newAccessMask);
        vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);
    }

    private static VkImageMemoryBarrier.Buffer createImageMemoryBarrier(MemoryStack stack, long imageId, int mipLevel, int oldLayout, int newLayout, int srcAccessMask, int dstAccessMask) {
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
        barrier.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
        barrier.oldLayout(oldLayout);
        barrier.newLayout(newLayout);
        barrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
        barrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
        barrier.image(imageId);
        barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
        barrier.subresourceRange().baseMipLevel(mipLevel);
        barrier.subresourceRange().levelCount(1);
        barrier.subresourceRange().baseArrayLayer(0);
        barrier.subresourceRange().layerCount(1);
        barrier.srcAccessMask(srcAccessMask);
        barrier.dstAccessMask(dstAccessMask);
        return barrier;
    }

    private static VkImageBlit.Buffer createImageBlit(MemoryStack stack, VulkanImage image, int level) {
        int prevLevel = level - 1;

        VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
        blit.srcOffsets(0, VkOffset3D.calloc(stack).set(0, 0, 0));
        blit.srcOffsets(1, VkOffset3D.calloc(stack).set(image.width >> prevLevel, image.height >> prevLevel, 1));
        blit.srcSubresource()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .mipLevel(prevLevel)
            .baseArrayLayer(0)
            .layerCount(1);

        blit.dstOffsets(0, VkOffset3D.calloc(stack).set(0, 0, 0));
        blit.dstOffsets(1, VkOffset3D.calloc(stack).set(image.width >> level, image.height >> level, 1));
        blit.dstSubresource()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .mipLevel(level)
            .baseArrayLayer(0)
            .layerCount(1);

        return blit;
    }

    private static void transitionImageLayoutForMipmaps(MemoryStack stack, VkCommandBuffer commandBuffer, VulkanImage image) {
        VkImageMemoryBarrier.Buffer barrier = createImageMemoryBarrier(stack, image.getId(), 0, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_SHADER_READ_BIT);
        barrier.subresourceRange().levelCount(image.mipLevels - 1);
        vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, barrier);

        barrier = createImageMemoryBarrier(stack, image.getId(), image.mipLevels - 1, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
        vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, barrier);

        image.setCurrentLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    }
}
