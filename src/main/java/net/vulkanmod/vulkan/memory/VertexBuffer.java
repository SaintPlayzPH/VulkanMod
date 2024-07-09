package net.vulkanmod.vulkan.memory;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

public class VertexBuffer extends Buffer {

    public VertexBuffer(int size, MemoryType type) {
        super(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, type);
        createBuffer(size);
    }

    public void copyToVertexBuffer(long vertexSize, long vertexCount, ByteBuffer byteBuffer) {
        int bufferSize = calculateBufferSize(vertexSize, vertexCount);

        if (needsResize(bufferSize)) {
            resizeBuffer(calculateNewSize(bufferSize));
        }

        type.copyToBuffer(this, bufferSize, byteBuffer);
        updateBufferUsage(bufferSize);
    }

    private int calculateBufferSize(long vertexSize, long vertexCount) {
        return (int) (vertexSize * vertexCount);
    }

    private boolean needsResize(int bufferSize) {
        return bufferSize > getBufferSize() - getUsedBytes();
    }

    private int calculateNewSize(int bufferSize) {
        return (getBufferSize() + bufferSize) * 2;
    }

    private void resizeBuffer(int newSize) {
        type.freeBuffer(this);
        createBuffer(newSize);
    }

    private void updateBufferUsage(int bufferSize) {
        setOffset(getUsedBytes());
        setUsedBytes(getUsedBytes() + bufferSize);
    }
}
