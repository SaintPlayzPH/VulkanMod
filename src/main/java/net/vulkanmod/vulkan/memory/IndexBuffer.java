package net.vulkanmod.vulkan.memory;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;

public class IndexBuffer extends Buffer {

    public IndexBuffer(int size, MemoryType type) {
        super(VK_BUFFER_USAGE_INDEX_BUFFER_BIT, type);
        this.createBuffer(size);
    }

    public void copyBuffer(ByteBuffer buffer) {
        int size = buffer.remaining();

        if (size > this.getBufferSize() - this.getUsedBytes()) {
            throw new RuntimeException("Trying to write buffer beyond max size.");
        } else {
            this.getType().copyToBuffer(this, size, buffer);
            setOffset(getUsedBytes());
            setUsedBytes(getUsedBytes() + size);
        }
    }

    private void resizeBuffer(int newSize) {
        this.getType().freeBuffer(this);
        this.createBuffer(newSize);
    }

    public enum IndexType {
        SHORT(2),
        INT(4);

        public final int size;

        IndexType(int size) {
            this.size = size;
        }
    }
}
