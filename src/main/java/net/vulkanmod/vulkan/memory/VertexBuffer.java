
package net.vulkanmod.vulkan.memory;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

public class VertexBuffer extends Buffer {

    public VertexBuffer(int size, MemoryType type) {
        super(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, type);
        this.createBuffer(size);
    }

    public void copyToVertexBuffer(long vertexSize, long vertexCount, ByteBuffer byteBuffer) {
        int bufferSize = (int) (vertexSize * vertexCount);

        if (bufferSize > this.getBufferSize() - this.getUsedBytes()) {
            resizeBuffer((this.getBufferSize() + bufferSize) * 2);
        }

        this.getType().copyToBuffer(this, bufferSize, byteBuffer);
        setOffset(getUsedBytes());
        setUsedBytes(getUsedBytes() + bufferSize);
    }

    private void resizeBuffer(int newSize) {
        this.getType().freeBuffer(this);
        this.createBuffer(newSize);
    }
}
