package net.vulkanmod.vulkan.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.vulkanmod.interfaces.ShaderMixed;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkCommandBuffer;

public class DrawUtil {

    public static void blitToScreen() {
      defualtBlit();
        //fastBlit();
    }

    public static void fastBlit() {
        GraphicsPipeline blitPipeline = PipelineManager.getFastBlitPipeline();

        RenderSystem.disableCull();

        Renderer renderer = Renderer.getInstance();
        renderer.bindGraphicsPipeline(blitPipeline);
        renderer.uploadAndBindUBOs(blitPipeline);

        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();

        // Define vertices for a 90-degree rotated quad
        float[] vertices = {
            1.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f,
            -1.0f, 1.0f, 0.0f
        };

        // Draw the rotated quad
        VK11.vkCmdDraw(commandBuffer, vertices.length / 3, 1, 0, 0);

        RenderSystem.enableCull();
    }

    public static void defualtBlit() {
        Matrix4f matrix4f = new Matrix4f().setOrtho(0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F);
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack posestack = RenderSystem.getModelViewStack();
        posestack.pushPose();
        posestack.setIdentity();

        // Apply a 90-degree clockwise rotation
        posestack.mulPose(Vector3f.YP.rotationDegrees(-90));

        RenderSystem.applyModelViewMatrix();
        posestack.popPose();

        ShaderInstance shaderInstance = Minecraft.getInstance().gameRenderer.blitShader;
//        RenderSystem.setShader(() -> shaderInstance);

        Tesselator tesselator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(-1.0D, -1.0D, 0.0D).uv(0.0F, 1.0F).endVertex();
        bufferBuilder.vertex(1.0D, -1.0D, 0.0D).uv(1.0F, 1.0F).endVertex();
        bufferBuilder.vertex(1.0D, 1.0D, 0.0D).uv(1.0F, 0.0F).endVertex();
        bufferBuilder.vertex(-1.0D, 1.0D, 0.0D).uv(0.0F, 0.0F).endVertex();
        var buffer = bufferBuilder.end();

        buffer.release();

        BufferBuilder.DrawState parameters = buffer.drawState();

        Renderer renderer = Renderer.getInstance();

        GraphicsPipeline pipeline = ((ShaderMixed)(shaderInstance)).getPipeline();
        renderer.bindGraphicsPipeline(pipeline);
        renderer.uploadAndBindUBOs(pipeline);
        Renderer.getDrawer().draw(buffer.vertexBuffer(), parameters.mode(), parameters.format(), parameters.vertexCount());
    }
}
