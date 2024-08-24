package net.vulkanmod.vulkan.framebuffer;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.render.util.MathUtil;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.vulkanmod.vulkan.Vulkan.*;
import static net.vulkanmod.vulkan.device.DeviceManager.vkDevice;
import static net.vulkanmod.vulkan.util.VUtil.UINT32_MAX;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class SwapChain extends Framebuffer {
    // Necessary until tearing-control-unstable-v1 is fully implemented on all GPU Drivers for Wayland
    private static final int defUncappedMode = checkPresentMode(VK_PRESENT_MODE_IMMEDIATE_KHR, VK_PRESENT_MODE_MAILBOX_KHR);

    private final Long2ReferenceOpenHashMap<long[]> FBO_map = new Long2ReferenceOpenHashMap<>();

    private long swapChainId = VK_NULL_HANDLE;
    private List<VulkanImage> swapChainImages;
    private VkExtent2D extent2D;
    public boolean isBGRAformat;
    private boolean vsync = false;

    private int[] glIds;

    public SwapChain() {
        this.attachmentCount = 2;
        this.depthFormat = Vulkan.getDefaultDepthFormat();

        this.hasColorAttachment = true;
        this.hasDepthAttachment = true;

        recreate();
    }

    public void recreate() {
        if (this.depthAttachment != null) {
            this.depthAttachment.free();
            this.depthAttachment = null;
        }

        if (!DYNAMIC_RENDERING) {
            this.FBO_map.forEach((pass, framebuffers) -> Arrays.stream(framebuffers).forEach(id -> vkDestroyFramebuffer(getVkDevice(), id, null)));
            this.FBO_map.clear();
        }

        createSwapChain();
    }

    private void createSwapChain() {
        try (MemoryStack stack = stackPush()) {
            VkDevice device = Vulkan.getVkDevice();
            DeviceManager.SurfaceProperties surfaceProperties = DeviceManager.querySurfaceProperties(device.getPhysicalDevice(), stack);

            VkSurfaceFormatKHR surfaceFormat = getFormat(surfaceProperties.formats);
            int presentMode = getPresentMode(surfaceProperties.presentModes);
            VkExtent2D extent = getExtent(surfaceProperties.capabilities);

            if (extent.width() == 0 && extent.height() == 0) {
                if (this.swapChainId != VK_NULL_HANDLE) {
                    this.swapChainImages.forEach(image -> vkDestroyImageView(device, image.getImageView(), null));
                    vkDestroySwapchainKHR(device, this.swapChainId, null);
                    this.swapChainId = VK_NULL_HANDLE;
                }

                this.width = 0;
                this.height = 0;
                return;
            }

            int requestedImages = Math.max(Initializer.CONFIG.imageCount, surfaceProperties.capabilities.minImageCount());
            IntBuffer imageCount = stack.ints(requestedImages);

            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack);

            createInfo.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR);
            createInfo.surface(Vulkan.getSurface());

            // Image settings
            this.format = surfaceFormat.format();
            this.extent2D = VkExtent2D.create().set(extent);

            createInfo.minImageCount(requestedImages);
            createInfo.imageFormat(this.format);
            createInfo.imageColorSpace(surfaceFormat.colorSpace());
            createInfo.imageExtent(extent);
            createInfo.imageArrayLayers(1);
            createInfo.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);

            Queue.QueueFamilyIndices indices = Queue.getQueueFamilies();

            if (indices.graphicsFamily != indices.presentFamily) {
                createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT);
                createInfo.pQueueFamilyIndices(stack.ints(indices.graphicsFamily, indices.presentFamily));
            } else {
                createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
            }

            int preTransform;
            if ((surfaceProperties.capabilities.supportedTransforms() & VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR) != 0) {
                preTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
            } else {
                preTransform = surfaceProperties.capabilities.currentTransform();
            }
            createInfo.preTransform(preTransform);

            createInfo.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);
            createInfo.presentMode(presentMode);
            createInfo.clipped(true);

            createInfo.oldSwapchain(this.swapChainId);

            LongBuffer pSwapChain = stack.longs(VK_NULL_HANDLE);

            int result = vkCreateSwapchainKHR(device, createInfo, null, pSwapChain);
            Vulkan.checkResult(result, "Failed to create swap chain");

            if (this.swapChainId != VK_NULL_HANDLE) {
                this.swapChainImages.forEach(image -> vkDestroyImageView(device, image.getImageView(), null));
                vkDestroySwapchainKHR(device, this.swapChainId, null);
            }

            this.swapChainId = pSwapChain.get(0);

            vkGetSwapchainImagesKHR(device, this.swapChainId, imageCount, null);

            LongBuffer pSwapchainImages = stack.mallocLong(imageCount.get(0));

            Initializer.LOGGER.info("Requested Image Count -> " + requestedImages + " Actual Images -> " + imageCount.get(0));
            vkGetSwapchainImagesKHR(device, this.swapChainId, imageCount, pSwapchainImages);

            this.swapChainImages = new ArrayList<>(imageCount.get(0));

            this.width = extent2D.width();
            this.height = extent2D.height();

            for (int i = 0; i < pSwapchainImages.capacity(); i++) {
                long imageId = pSwapchainImages.get(i);
                long imageView = VulkanImage.createImageView(imageId, this.format, VK_IMAGE_ASPECT_COLOR_BIT, 1);

                VulkanImage image = new VulkanImage(imageId, this.format, 1, this.width, this.height, 4, 0, imageView);
                image.updateTextureSampler(true, true, false);
                this.swapChainImages.add(image);
            }
        }

        createGlIds();
        createDepthResources();
    }

    private void createGlIds() {
        this.glIds = new int[this.swapChainImages.size()];

        for (int i = 0; i < this.swapChainImages.size(); i++) {
            int id = GlTexture.genTextureId();
            this.glIds[i] = id;
            GlTexture.bindIdToImage(id, this.swapChainImages.get(i));
        }
    }

    public int getColorAttachmentGlId() {
        return this.glIds[Renderer.getCurrentImage()];
    }

    private long[] createFramebuffers(RenderPass renderPass) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long[] framebuffers = new long[this.swapChainImages.size()];

            for (int i = 0; i < this.swapChainImages.size(); ++i) {
                LongBuffer attachments = stack.longs(this.swapChainImages.get(i).getImageView(), this.depthAttachment.getImageView());

                LongBuffer pFramebuffer = stack.mallocLong(1);

                VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack);
                framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
                framebufferInfo.renderPass(renderPass.getId());
                framebufferInfo.width(this.width);
                framebufferInfo.height(this.height);
                framebufferInfo.layers(1);
                framebufferInfo.pAttachments(attachments);

                if (vkCreateFramebuffer(Vulkan.getVkDevice(), framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create framebuffer");
                }

                framebuffers[i] = pFramebuffer.get(0);
            }

            return framebuffers;
        }
    }

    private void createDepthResources() {
        this.depthAttachment = VulkanImage.createDepthImage(depthFormat, this.width, this.height,
                VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                false, false);
    }

    public void beginRenderPass(VkCommandBuffer commandBuffer, RenderPass renderPass, MemoryStack stack) {
        if (!DYNAMIC_RENDERING) {
            long[] framebufferId = this.FBO_map.computeIfAbsent(renderPass.id, renderPass1 -> createFramebuffers(renderPass));
            renderPass.beginRenderPass(commandBuffer, framebufferId[Renderer.getCurrentImageIndex()], this.width, this.height, stack);
        }
    }

    public void cleanup() {
        vkDeviceWaitIdle(vkDevice);

        if (this.depthAttachment != null) {
            this.depthAttachment.free();
            this.depthAttachment = null;
        }

        if (this.swapChainId != VK_NULL_HANDLE) {
            for (VulkanImage image : this.swapChainImages) {
                vkDestroyImageView(vkDevice, image.getImageView(), null);
            }
            vkDestroySwapchainKHR(vkDevice, this.swapChainId, null);
            this.swapChainId = VK_NULL_HANDLE;
        }

        if (!DYNAMIC_RENDERING) {
            this.FBO_map.forEach((pass, framebuffers) -> Arrays.stream(framebuffers).forEach(id -> vkDestroyFramebuffer(getVkDevice(), id, null)));
            this.FBO_map.clear();
        }
    }

    public void resize() {
        recreate();
    }

    public static VkSurfaceFormatKHR getFormat(VkSurfaceFormatKHR.Buffer formats) {
        for (int i = 0; i < formats.capacity(); i++) {
            if (formats.get(i).format() == VK_FORMAT_B8G8R8A8_SRGB || formats.get(i).format() == VK_FORMAT_R8G8B8A8_SRGB) {
                return formats.get(i);
            }
        }

        return formats.get(0);
    }

    public static int getPresentMode(IntBuffer presentModes) {
        for (int i = 0; i < presentModes.capacity(); i++) {
            if (presentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                return VK_PRESENT_MODE_MAILBOX_KHR;
            } else if (presentModes.get(i) == VK_PRESENT_MODE_FIFO_KHR) {
                return VK_PRESENT_MODE_FIFO_KHR;
            }
        }

        return presentModes.get(0);
    }

    private static int checkPresentMode(int... modes) {
        try (MemoryStack stack = stackGet()) {
            VkPhysicalDevice physicalDevice = Vulkan.getVkPhysicalDevice();
            VkSurfaceKHR surface = Vulkan.getSurface();
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.mallocStack(stack);
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities);

            IntBuffer pPresentModeCount = stack.mallocInt(1);
            vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, null);

            IntBuffer presentModes = stack.mallocInt(pPresentModeCount.get(0));
            vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, presentModes);

            for (int mode : modes) {
                for (int i = 0; i < presentModes.capacity(); i++) {
                    if (presentModes.get(i) == mode) {
                        return mode;
                    }
                }
            }

            return presentModes.get(0);
        }
    }

    private static VkExtent2D getExtent(VkSurfaceCapabilitiesKHR capabilities) {
        if (capabilities.currentExtent().width() != UINT32_MAX) {
            return capabilities.currentExtent();
        } else {
            try (MemoryStack stack = stackGet()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);

                // Assuming that this method would be replaced with the appropriate Android method
                // to get the dimensions of the window on Android.
                glfwGetFramebufferSize(Vulkan.getSurface(), width, height);

                VkExtent2D actualExtent = VkExtent2D.mallocStack(stack);
                actualExtent.width(MathUtil.clamp(width.get(0), capabilities.minImageExtent().width(), capabilities.maxImageExtent().width()));
                actualExtent.height(MathUtil.clamp(height.get(0), capabilities.minImageExtent().height(), capabilities.maxImageExtent().height()));

                return actualExtent;
            }
        }
    }

    public List<VulkanImage> getSwapChainImages() {
        return swapChainImages;
    }

    public long getSwapChainId() {
        return swapChainId;
    }

    public boolean isVsync() {
        return vsync;
    }

    public void setVsync(boolean vsync) {
        this.vsync = vsync;
    }
}
