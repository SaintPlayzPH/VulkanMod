package net.vulkanmod.vulkan.framebuffer;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class RenderPass {
    private final Framebuffer framebuffer;
    private long id;

    private final int attachmentCount;
    private final AttachmentInfo colorAttachmentInfo;
    private final AttachmentInfo depthAttachmentInfo;

    public RenderPass(Framebuffer framebuffer, AttachmentInfo colorAttachmentInfo, AttachmentInfo depthAttachmentInfo) {
        this.framebuffer = framebuffer;
        this.colorAttachmentInfo = colorAttachmentInfo;
        this.depthAttachmentInfo = depthAttachmentInfo;

        this.attachmentCount = (colorAttachmentInfo != null ? 1 : 0) + (depthAttachmentInfo != null ? 1 : 0);

        if (!Vulkan.DYNAMIC_RENDERING) {
            framebuffer.addRenderPass(this);
            createRenderPass();
        }
    }

    private void createRenderPass() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(attachmentCount, stack);
            VkAttachmentReference.Buffer attachmentRefs = VkAttachmentReference.calloc(attachmentCount, stack);

            VkSubpassDescription subpass = VkSubpassDescription.calloc(stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);

            int index = 0;
            if (colorAttachmentInfo != null) {
                setupAttachment(stack, attachments.get(index), attachmentRefs.get(index), colorAttachmentInfo, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                subpass.pColorAttachments(VkAttachmentReference.calloc(1, stack).put(0, attachmentRefs.get(index)));
                index++;
            }

            if (depthAttachmentInfo != null) {
                setupAttachment(stack, attachments.get(index), attachmentRefs.get(index), depthAttachmentInfo, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
                subpass.pDepthStencilAttachment(attachmentRefs.get(index));
            }

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(attachments)
                    .pSubpasses(subpass);

            setupSubpassDependencies(stack, renderPassInfo, colorAttachmentInfo.finalLayout);

            LongBuffer pRenderPass = stack.mallocLong(1);
            if (vkCreateRenderPass(Vulkan.getVkDevice(), renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create render pass");
            }

            id = pRenderPass.get(0);
        }
    }

    private void setupAttachment(MemoryStack stack, VkAttachmentDescription attachment, VkAttachmentReference reference, AttachmentInfo info, int layout) {
        attachment.format(info.format)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(info.loadOp)
                .storeOp(info.storeOp)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(layout)
                .finalLayout(info.finalLayout);

        reference.attachment(0)
                .layout(layout);
    }

    private void setupSubpassDependencies(MemoryStack stack, VkRenderPassCreateInfo renderPassInfo, int finalLayout) {
        VkSubpassDependency.Buffer subpassDependencies = VkSubpassDependency.calloc(1, stack);
        if (finalLayout == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR) {
            subpassDependencies.get(0)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                    .srcAccessMask(0)
                    .dstAccessMask(0);
        } else if (finalLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            subpassDependencies.get(0)
                    .srcSubpass(0)
                    .dstSubpass(VK_SUBPASS_EXTERNAL)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
        }
        renderPassInfo.pDependencies(subpassDependencies);
    }

    public void beginRenderPass(VkCommandBuffer commandBuffer, long framebufferId, MemoryStack stack) {
        transitionAttachmentsLayout(stack, commandBuffer);

        VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType$Default()
                .renderPass(this.id)
                .framebuffer(framebufferId)
                .renderArea(VkRect2D.calloc(stack).offset(VkOffset2D.calloc(stack).set(0, 0)).extent(VkExtent2D.calloc(stack).set(framebuffer.getWidth(), framebuffer.getHeight())))
                .pClearValues(createClearValues(stack));

        vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

        Renderer.getInstance().setBoundRenderPass(this);
    }

    private void transitionAttachmentsLayout(MemoryStack stack, VkCommandBuffer commandBuffer) {
        if (colorAttachmentInfo != null && framebuffer.getColorAttachment().getCurrentLayout() != VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
            framebuffer.getColorAttachment().transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        }
        if (depthAttachmentInfo != null && framebuffer.getDepthAttachment().getCurrentLayout() != VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
            framebuffer.getDepthAttachment().transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        }
    }

    private VkClearValue.Buffer createClearValues(MemoryStack stack) {
        VkClearValue.Buffer clearValues = VkClearValue.calloc(2, stack);
        clearValues.get(0).color().float32(VRenderSystem.clearColor);
        clearValues.get(1).depthStencil().set(1.0f, 0);
        return clearValues;
    }

    public void endRenderPass(VkCommandBuffer commandBuffer) {
        vkCmdEndRenderPass(commandBuffer);
        updateAttachmentLayouts();

        Renderer.getInstance().setBoundRenderPass(null);
    }

    private void updateAttachmentLayouts() {
        if (colorAttachmentInfo != null) {
            framebuffer.getColorAttachment().setCurrentLayout(colorAttachmentInfo.finalLayout);
        }
        if (depthAttachmentInfo != null) {
            framebuffer.getDepthAttachment().setCurrentLayout(depthAttachmentInfo.finalLayout);
        }
    }

    public void beginDynamicRendering(VkCommandBuffer commandBuffer, MemoryStack stack) {
        VkRenderingInfo renderingInfo = VkRenderingInfo.calloc(stack)
                .sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_INFO_KHR)
                .renderArea(VkRect2D.calloc(stack).offset(VkOffset2D.calloc(stack).set(0, 0)).extent(VkExtent2D.calloc(stack).set(framebuffer.getWidth(), framebuffer.getHeight())))
                .layerCount(1);

        if (colorAttachmentInfo != null) {
            renderingInfo.pColorAttachments(createColorAttachment(stack));
        }
        if (depthAttachmentInfo != null) {
            renderingInfo.pDepthAttachment(createDepthAttachment(stack));
        }

        KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, renderingInfo);
    }

    private VkRenderingAttachmentInfo.Buffer createColorAttachment(MemoryStack stack) {
        return VkRenderingAttachmentInfo.calloc(1, stack)
                .sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR)
                .imageView(framebuffer.getColorAttachment().getImageView())
                .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .loadOp(colorAttachmentInfo.loadOp)
                .storeOp(colorAttachmentInfo.storeOp)
                .clearValue(createClearValues(stack).get(0));
    }

    private VkRenderingAttachmentInfo createDepthAttachment(MemoryStack stack) {
        return VkRenderingAttachmentInfo.calloc(stack)
                .sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR)
                .imageView(framebuffer.getDepthAttachment().getImageView())
                .imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                .loadOp(depthAttachmentInfo.loadOp)
                .storeOp(depthAttachmentInfo.storeOp)
                .clearValue(createClearValues(stack).get(1));
    }

    public void endDynamicRendering(VkCommandBuffer commandBuffer) {
        KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
    }

    public Framebuffer getFramebuffer() {
        return framebuffer;
    }

    public void cleanUp() {
        if (!Vulkan.DYNAMIC_RENDERING) {
            MemoryManager.getInstance().addFrameOp(() -> vkDestroyRenderPass(Vulkan.getVkDevice(), this.id, null));
        }
    }

    public long getId() {
        return id;
    }

    public static class AttachmentInfo {
        final Type type;
        final int format;
        int finalLayout;
        int loadOp;
        int storeOp;

        public AttachmentInfo(Type type, int format) {
            this.type = type;
            this.format = format;
            this.finalLayout = type.defaultLayout;
            this.loadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
            this.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        }

        public AttachmentInfo setOps(int loadOp, int storeOp) {
            this.loadOp = loadOp;
            this.storeOp = storeOp;
            return this;
        }

        public AttachmentInfo setLoadOp(int loadOp) {
            this.loadOp = loadOp;
            return this;
        }

        public AttachmentInfo setFinalLayout(int finalLayout) {
            this.finalLayout = finalLayout;
            return this;
        }

        public enum Type {
            COLOR(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL),
            DEPTH(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            final int defaultLayout;

            Type(int layout) {
                defaultLayout = layout;
            }
        }
    }

    public static Builder builder(Framebuffer framebuffer) {
        return new Builder(framebuffer);
    }

    public static class Builder {
        private final Framebuffer framebuffer;
        private AttachmentInfo colorAttachmentInfo;
        private AttachmentInfo depthAttachmentInfo;

        public Builder(Framebuffer framebuffer) {
            this.framebuffer = framebuffer;
            if (framebuffer.hasColorAttachment) {
                colorAttachmentInfo = new AttachmentInfo(AttachmentInfo.Type.COLOR, framebuffer.format)
                        .setOps(VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_STORE);
            }
            if (framebuffer.hasDepthAttachment) {
                depthAttachmentInfo = new AttachmentInfo(AttachmentInfo.Type.DEPTH, framebuffer.depthFormat)
                        .setOps(VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_DONT_CARE);
            }
        }

        public RenderPass build() {
            return new RenderPass(framebuffer, colorAttachmentInfo, depthAttachmentInfo);
        }

        public Builder setColorAttachmentInfo(AttachmentInfo colorAttachmentInfo) {
            this.colorAttachmentInfo = colorAttachmentInfo;
            return this;
        }

        public Builder setDepthAttachmentInfo(AttachmentInfo depthAttachmentInfo) {
            this.depthAttachmentInfo = depthAttachmentInfo;
            return this;
        }

        public Builder setLoadOp(int loadOp) {
            if (colorAttachmentInfo != null) {
                colorAttachmentInfo.setLoadOp(loadOp);
            }
            if (depthAttachmentInfo != null) {
                depthAttachmentInfo.setLoadOp(loadOp);
            }
            return this;
        }
    }
}
