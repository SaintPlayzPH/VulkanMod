package net.vulkanmod.render.chunk.buffer;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.render.chunk.ChunkArea;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.chunk.util.StaticQueue;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.IndirectBuffer;
import net.vulkanmod.vulkan.shader.Pipeline;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.util.EnumMap;

import static org.lwjgl.vulkan.VK10.*;

public class DrawBuffers {

    private static final int VERTEX_SIZE = PipelineManager.TERRAIN_VERTEX_FORMAT.getVertexSize();
    private static final int INDEX_SIZE = Short.BYTES;
    private static final float POS_OFFSET = PipelineManager.TERRAIN_VERTEX_FORMAT == CustomVertexFormat.COMPRESSED_TERRAIN ? 4.0f : 0.0f;

    private final int index;
    private final Vector3i origin;
    private final int minHeight;

    private boolean allocated = false;
    private AreaBuffer vertexBuffer, indexBuffer;
    private final EnumMap<TerrainRenderType, AreaBuffer> vertexBuffers = new EnumMap<>(TerrainRenderType.class);

    public DrawBuffers(int index, Vector3i origin, int minHeight) {
        this.index = index;
        this.origin = origin;
        this.minHeight = minHeight;
    }

    public void allocateBuffers() {
        if (!Initializer.CONFIG.perRenderTypeAreaBuffers) {
            vertexBuffer = new AreaBuffer(AreaBuffer.Usage.VERTEX, 2097152, VERTEX_SIZE);
        }
        this.allocated = true;
    }

    public void upload(RenderSection section, UploadBuffer buffer, TerrainRenderType renderType) {
        DrawParameters drawParameters = section.getDrawParameters(renderType);

        if (!buffer.indexOnly) {
            AreaBuffer.Segment segment = getOrAllocateAreaBuffer(renderType).upload(buffer.getVertexBuffer(), drawParameters.vertexOffset, drawParameters);
            drawParameters.vertexOffset = segment.offset / VERTEX_SIZE;
            drawParameters.baseInstance = encodeSectionOffset(section.xOffset(), section.yOffset(), section.zOffset());
        }

        if (!buffer.autoIndices) {
            if (indexBuffer == null) {
                indexBuffer = new AreaBuffer(AreaBuffer.Usage.INDEX, 786432, INDEX_SIZE);
            }
            AreaBuffer.Segment segment = indexBuffer.upload(buffer.getIndexBuffer(), drawParameters.firstIndex, drawParameters);
            drawParameters.firstIndex = segment.offset / INDEX_SIZE;
        }

        drawParameters.indexCount = buffer.indexCount;
        drawParameters.instanceCount = drawParameters.vertexOffset == -1 ? 0 : 1;
        drawParameters.firstIndex = drawParameters.firstIndex == -1 ? 0 : drawParameters.firstIndex;

        buffer.release();
    }

    private AreaBuffer getOrAllocateAreaBuffer(TerrainRenderType renderType) {
        return vertexBuffers.computeIfAbsent(renderType,
                rt -> Initializer.CONFIG.perRenderTypeAreaBuffers ? new AreaBuffer(AreaBuffer.Usage.VERTEX, rt.initialSize, VERTEX_SIZE) : vertexBuffer);
    }

    private int encodeSectionOffset(int xOffset, int yOffset, int zOffset) {
        final int x = xOffset & 127;
        final int y = (yOffset - minHeight) & 127;
        final int z = zOffset & 127;
        return (y << 16) | (z << 8) | x;
    }

    private void updateChunkAreaOrigin(VkCommandBuffer commandBuffer, Pipeline pipeline, double camX, double camY, double camZ, MemoryStack stack) {
        float xOffset = (float) (origin.x + POS_OFFSET - camX);
        float yOffset = (float) (origin.y + POS_OFFSET - camY);
        float zOffset = (float) (origin.z + POS_OFFSET - camZ);

        ByteBuffer byteBuffer = stack.malloc(12);
        byteBuffer.putFloat(0, xOffset).putFloat(4, yOffset).putFloat(8, zOffset);

        vkCmdPushConstants(commandBuffer, pipeline.getLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, byteBuffer);
    }

    public void buildDrawBatchesIndirect(IndirectBuffer indirectBuffer, StaticQueue<RenderSection> queue, TerrainRenderType terrainRenderType) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer byteBuffer = stack.malloc(20 * queue.size());
            long bufferPtr = MemoryUtil.memAddress(byteBuffer);

            int drawCount = 0;

            for (RenderSection section : queue.iterator(terrainRenderType == TerrainRenderType.TRANSLUCENT)) {
                DrawParameters drawParameters = section.getDrawParameters(terrainRenderType);
                if (drawParameters.indexCount <= 0) continue;

                long ptr = bufferPtr + (drawCount * 20L);
                MemoryUtil.memPutInt(ptr, drawParameters.indexCount);
                MemoryUtil.memPutInt(ptr + 4, drawParameters.instanceCount);
                MemoryUtil.memPutInt(ptr + 8, drawParameters.firstIndex);
                MemoryUtil.memPutInt(ptr + 12, drawParameters.vertexOffset);
                MemoryUtil.memPutInt(ptr + 16, drawParameters.baseInstance);

                drawCount++;
            }

            if (drawCount > 0) {
                indirectBuffer.recordCopyCmd(byteBuffer);
                vkCmdDrawIndexedIndirect(Renderer.getCommandBuffer(), indirectBuffer.getId(), indirectBuffer.getOffset(), drawCount, 20);
            }
        }
    }

    public void buildDrawBatchesDirect(StaticQueue<RenderSection> queue, TerrainRenderType renderType) {
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();

        for (RenderSection section : queue.iterator(renderType == TerrainRenderType.TRANSLUCENT)) {
            DrawParameters drawParameters = section.getDrawParameters(renderType);
            if (drawParameters.indexCount <= 0) continue;

            vkCmdDrawIndexed(commandBuffer, drawParameters.indexCount, drawParameters.instanceCount,
                    drawParameters.firstIndex, drawParameters.vertexOffset, drawParameters.baseInstance);
        }
    }

    public void bindBuffers(VkCommandBuffer commandBuffer, Pipeline pipeline, TerrainRenderType terrainRenderType, double camX, double camY, double camZ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AreaBuffer vertexBuffer = getOrAllocateAreaBuffer(terrainRenderType);
            nvkCmdBindVertexBuffers(commandBuffer, 0, 1, stack.npointer(vertexBuffer.getId()), stack.npointer(0));
            updateChunkAreaOrigin(commandBuffer, pipeline, camX, camY, camZ, stack);
        }

        if (terrainRenderType == TerrainRenderType.TRANSLUCENT) {
            vkCmdBindIndexBuffer(commandBuffer, indexBuffer.getId(), 0, VK_INDEX_TYPE_UINT16);
        }
    }

    public void releaseBuffers() {
        if (!allocated) return;

        if (vertexBuffer == null) {
            vertexBuffers.values().forEach(AreaBuffer::freeBuffer);
        } else {
            vertexBuffer.freeBuffer();
        }

        if (indexBuffer != null) {
            indexBuffer.freeBuffer();
        }

        vertexBuffers.clear();
        vertexBuffer = null;
        indexBuffer = null;
        allocated = false;
    }

    public boolean isAllocated() {
        return allocated;
    }

    public AreaBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    public EnumMap<TerrainRenderType, AreaBuffer> getVertexBuffers() {
        return vertexBuffers;
    }

    public AreaBuffer getIndexBuffer() {
        return indexBuffer;
    }

    public static class DrawParameters {
        int indexCount = 0, instanceCount = 1, firstIndex = -1, vertexOffset = -1, baseInstance;

        public void reset(ChunkArea chunkArea, TerrainRenderType renderType) {
            int segmentOffset = vertexOffset * VERTEX_SIZE;
            if (chunkArea != null && chunkArea.getDrawBuffers().hasRenderType(renderType) && segmentOffset != -1) {
                chunkArea.getDrawBuffers().getAreaBuffer(renderType).setSegmentFree(segmentOffset);
            }

            indexCount = 0;
            firstIndex = -1;
            vertexOffset = -1;
        }
    }
}
