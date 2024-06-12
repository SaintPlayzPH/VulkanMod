package net.vulkanmod.vulkan.memory;

import net.vulkanmod.Initializer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class AutoIndexBuffer {
    int vertexCount;
    DrawType drawType;
    IndexBuffer indexBuffer;

    public AutoIndexBuffer(int vertexCount, DrawType type) {
        this.drawType = type;
        createIndexBuffer(vertexCount);
    }

    private void createIndexBuffer(int vertexCount) {
        this.vertexCount = vertexCount;
        ByteBuffer buffer;

        switch (this.drawType) {
            case QUADS -> buffer = genQuadIndices(vertexCount);
            case TRIANGLE_FAN -> buffer = genTriangleFanIndices(vertexCount);
            case TRIANGLE_STRIP -> buffer = genTriangleStripIndices(vertexCount);
            case LINES -> buffer = genLinesIndices(vertexCount);
            case DEBUG_LINE_STRIP -> buffer = genDebugLineStripIndices(vertexCount);
            default -> throw new IllegalArgumentException("Unsupported drawType: " + this.drawType);
        }

        this.indexBuffer = new IndexBuffer(buffer.capacity(), MemoryType.GPU_MEM);
        this.indexBuffer.copyBuffer(buffer);

        MemoryUtil.memFree(buffer);
    }

    public void checkCapacity(int vertexCount) {
        if (vertexCount > this.vertexCount) {
            int newVertexCount = this.vertexCount * 2;
            Initializer.LOGGER.info("Reallocating AutoIndexBuffer from {} to {}", this.vertexCount, newVertexCount);

            this.indexBuffer.freeBuffer();
            createIndexBuffer(newVertexCount);
        }
    }

    public static ByteBuffer genQuadIndices(int vertexCount) {
        int indexCount = roundUpToDivisible(vertexCount * 3 / 2, 6);

        ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * Short.BYTES);
        ShortBuffer idxs = buffer.asShortBuffer();

        for (int i = 0, j = 0; i < vertexCount; i += 4, j += 6) {
            idxs.put(j, (short) i)
                .put(j + 1, (short) (i + 1))
                .put(j + 2, (short) (i + 2))
                .put(j + 3, (short) i)
                .put(j + 4, (short) (i + 2))
                .put(j + 5, (short) (i + 3));
        }

        return buffer;
    }

    public static ByteBuffer genLinesIndices(int vertexCount) {
        int indexCount = roundUpToDivisible(vertexCount * 3 / 2, 6);

        ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * Short.BYTES);
        ShortBuffer idxs = buffer.asShortBuffer();

        for (int i = 0, j = 0; i < vertexCount; i += 4, j += 6) {
            idxs.put(j, (short) i)
                .put(j + 1, (short) (i + 1))
                .put(j + 2, (short) (i + 2))
                .put(j + 3, (short) (i + 3))
                .put(j + 4, (short) (i + 2))
                .put(j + 5, (short) (i + 1));
        }

        return buffer;
    }

    public static ByteBuffer genTriangleFanIndices(int vertexCount) {
        int indexCount = (vertexCount - 2) * 3;
        ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * Short.BYTES);
        ShortBuffer idxs = buffer.asShortBuffer();

        for (int i = 0, j = 0; i < vertexCount - 2; ++i, j += 3) {
            idxs.put(j, (short) 0)
                .put(j + 1, (short) (i + 1))
                .put(j + 2, (short) (i + 2));
        }

        return buffer;
    }

    public static ByteBuffer genTriangleStripIndices(int vertexCount) {
        int indexCount = (vertexCount - 2) * 3;
        ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * Short.BYTES);
        ShortBuffer idxs = buffer.asShortBuffer();

        for (int i = 0, j = 0; i < vertexCount - 2; ++i, j += 3) {
            idxs.put(j, (short) i)
                .put(j + 1, (short) (i + 1))
                .put(j + 2, (short) (i + 2));
        }

        return buffer;
    }

    public static ByteBuffer genDebugLineStripIndices(int vertexCount) {
        int indexCount = (vertexCount - 1) * 2;
        ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * Short.BYTES);
        ShortBuffer idxs = buffer.asShortBuffer();

        for (int i = 0, j = 0; i < vertexCount - 1; ++i, j += 2) {
            idxs.put(j, (short) i)
                .put(j + 1, (short) (i + 1));
        }

        return buffer;
    }

    public static int roundUpToDivisible(int n, int d) {
        return ((n + d - 1) / d) * d;
    }

    public IndexBuffer getIndexBuffer() {
        return this.indexBuffer;
    }

    public void freeBuffer() {
        this.indexBuffer.freeBuffer();
    }

    public enum DrawType {
        QUADS,
        TRIANGLE_FAN,
        TRIANGLE_STRIP,
        DEBUG_LINE_STRIP,
        LINES;

        public static int getIndexCount(DrawType drawType, int vertexCount) {
            return switch (drawType) {
                case QUADS, LINES -> vertexCount * 3 / 2;
                case TRIANGLE_FAN, TRIANGLE_STRIP -> (vertexCount - 2) * 3;
                default -> 0;
            };
        }
    }
}
