package net.vulkanmod.vulkan.framebuffer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2LongArrayMap;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.apache.commons.lang3.Validate;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.Arrays;

import static net.vulkanmod.vulkan.Vulkan.DYNAMIC_RENDERING;
import static org.lwjgl.vulkan.VK10.*;

public class Framebuffer {
    public static final int DEFAULT_FORMAT = VK_FORMAT_R8G8B8A8_UNORM;

    protected int format;
    protected int depthFormat;
    protected int width, height;
    protected boolean linearFiltering;
    protected boolean depthLinearFiltering;
    protected int attachmentCount;

    boolean hasColorAttachment;
    boolean hasDepthAttachment;

    private VulkanImage colorAttachment;
    protected VulkanImage depthAttachment;

    private final ObjectArrayList<RenderPass> renderPasses = new ObjectArrayList<>();
    private final Reference2LongArrayMap<RenderPass> framebufferIds = new Reference2LongArrayMap<>();

    protected Framebuffer() {}

    public Framebuffer(Builder builder) {
        this.format = builder.format;
        this.depthFormat = builder.depthFormat;
        this.width = builder.width;
        this.height = builder.height;
        this.linearFiltering = builder.linearFiltering;
        this.depthLinearFiltering = builder.depthLinearFiltering;
        this.hasColorAttachment = builder.hasColorAttachment;
        this.hasDepthAttachment = builder.hasDepthAttachment;

        if (builder.createImages) {
            this.createImages();
        } else {
            this.colorAttachment = builder.colorAttachment;
            this.depthAttachment = builder.depthAttachment;
        }
    }

    public void addRenderPass(RenderPass renderPass) {
        this.renderPasses.add(renderPass);
    }

    public void createImages() {
        if (this.hasColorAttachment) {
            this.colorAttachment = VulkanImage.builder(this.width, this.height)
                    .setFormat(format)
                    .setUsage(!Initializer.CONFIG.dontUseImageSampled ? VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT : VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .setLinearFiltering(linearFiltering)
                    .setClamp(true)
                    .createVulkanImage();
        }

        if (this.hasDepthAttachment) {
            this.depthAttachment = VulkanImage.createDepthImage(depthFormat, this.width, this.height,
                    !Initializer.CONFIG.dontUseImageSampled ? VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT : VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                    depthLinearFiltering, true);

            this.attachmentCount++;
        }
    }

    public void resize(int newWidth, int newHeight) {
        if (this.width == newWidth && this.height == newHeight) return; // Avoid unnecessary resize

        this.width = newWidth;
        this.height = newHeight;

        this.cleanUp();
        this.createImages();
    }

    private long createFramebuffer(RenderPass renderPass) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer attachments = createAttachmentsBuffer(stack);

            LongBuffer pFramebuffer = stack.mallocLong(1);

            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass.getId())
                    .width(this.width)
                    .height(this.height)
                    .layers(1)
                    .pAttachments(attachments);

            if (vkCreateFramebuffer(Vulkan.getVkDevice(), framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create framebuffer");
            }

            return pFramebuffer.get(0);
        }
    }

    private LongBuffer createAttachmentsBuffer(MemoryStack stack) {
        if (colorAttachment != null && depthAttachment != null) {
            return stack.longs(colorAttachment.getImageView(), depthAttachment.getImageView());
        } else if (colorAttachment != null) {
            return stack.longs(colorAttachment.getImageView());
        } else {
            throw new IllegalStateException("No attachments found");
        }
    }

    public void beginRenderPass(VkCommandBuffer commandBuffer, RenderPass renderPass, MemoryStack stack) {
        if (!DYNAMIC_RENDERING) {
            long framebufferId = framebufferIds.computeIfAbsent(renderPass, this::createFramebuffer);
            renderPass.beginRenderPass(commandBuffer, framebufferId, stack);
        } else {
            renderPass.beginDynamicRendering(commandBuffer, stack);
        }

        Renderer.getInstance().setBoundRenderPass(renderPass);
        Renderer.getInstance().setBoundFramebuffer(this);
    }

    public VkViewport.Buffer viewport(MemoryStack stack) {
        return VkViewport.malloc(1, stack)
                .x(0.0f)
                .y(this.height)
                .width(this.width)
                .height(-this.height)
                .minDepth(0.0f)
                .maxDepth(1.0f);
    }

    public VkRect2D.Buffer scissor(MemoryStack stack) {
        VkRect2D.Buffer scissor = VkRect2D.mallocStack(1, stack);
        scissor.offset(VkOffset2D.callocStack(stack).set(0, 0));
        scissor.extent(VkExtent2D.callocStack(stack).set(this.width, this.height));
        return scissor;
    }
    
    public void cleanUp() {
        cleanUp(true);
    }

    public void cleanUp(boolean cleanImages) {
        if (cleanImages) {
            if (this.colorAttachment != null) this.colorAttachment.free();
            if (this.depthAttachment != null) this.depthAttachment.free();
        }

        final VkDevice device = Vulkan.getVkDevice();
        final var ids = framebufferIds.values().toLongArray();

        MemoryManager.getInstance().addFrameOp(() -> Arrays.stream(ids).forEach(id -> vkDestroyFramebuffer(device, id, null)));

        framebufferIds.clear();
    }

    public long getDepthImageView() {
        return depthAttachment.getImageView();
    }

    public VulkanImage getDepthAttachment() {
        return depthAttachment;
    }

    public VulkanImage getColorAttachment() {
        return colorAttachment;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getFormat() {
        return this.format;
    }

    public int getDepthFormat() {
        return this.depthFormat;
    }

    public static Builder builder(int width, int height, int colorAttachments, boolean hasDepthAttachment) {
        return new Builder(width, height, colorAttachments, hasDepthAttachment);
    }

    public static Builder builder(VulkanImage colorAttachment, VulkanImage depthAttachment) {
        return new Builder(colorAttachment, depthAttachment);
    }

    public static class Builder {
        final boolean createImages;
        final int width, height;
        int format, depthFormat;
        VulkanImage colorAttachment;
        VulkanImage depthAttachment;
        boolean hasColorAttachment;
        boolean hasDepthAttachment;
        boolean linearFiltering;
        boolean depthLinearFiltering;

        public Builder(int width, int height, int colorAttachments, boolean hasDepthAttachment) {
            Validate.isTrue(colorAttachments > 0 || hasDepthAttachment, "At least 1 attachment needed");
            Validate.isTrue(colorAttachments <= 1, "Multiple color attachments not supported");

            this.createImages = true;
            this.format = DEFAULT_FORMAT;
            this.depthFormat = Vulkan.getDefaultDepthFormat();
            this.linearFiltering = true;
            this.depthLinearFiltering = false;
            this.width = width;
            this.height = height;
            this.hasColorAttachment = colorAttachments == 1;
            this.hasDepthAttachment = hasDepthAttachment;
        }

        public Builder(VulkanImage colorAttachment, VulkanImage depthAttachment) {
            this.createImages = false;
            this.colorAttachment = colorAttachment;
            this.depthAttachment = depthAttachment;
            this.format = colorAttachment.format;
            this.width = colorAttachment.width;
            this.height = colorAttachment.height;
            this.hasColorAttachment = true;
            this.hasDepthAttachment = depthAttachment != null;
            this.depthFormat = this.hasDepthAttachment ? depthAttachment.format : 0;
            this.linearFiltering = true;
            this.depthLinearFiltering = false;
        }

        public Framebuffer build() {
            return new Framebuffer(this);
        }

        public Builder setFormat(int format) {
            this.format = format;
            return this;
        }

        public Builder setLinearFiltering(boolean linearFiltering) {
            this.linearFiltering = linearFiltering;
            return this;
        }

        public Builder setDepthLinearFiltering(boolean depthLinearFiltering) {
            this.depthLinearFiltering = depthLinearFiltering;
            return this;
        }
    }
}
