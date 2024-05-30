package net.vulkanmod.vulkan.memory;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;

public class IndexBuffer extends Buffer {
    private static final int DEFAULT_INITIAL_SIZE = 1024; // Default initial size for the buffer

    public IndexBuffer(MemoryType type) {
        super(VK_BUFFER_USAGE_INDEX_BUFFER_BIT, type);
    }

    public void copyBuffer(ByteBuffer buffer) {
        int size = buffer.remaining();

        if (size > remainingCapacity()) {
            resizeBuffer(Math.max(size, this.bufferSize * 2)); // Resize if necessary
        }

        type.copyToBuffer(this, size, buffer);

        offset = usedBytes;
        usedBytes += size;
    }

    private int remainingCapacity() {
        return this.bufferSize - this.usedBytes;
    }

    private void resizeBuffer(int newSize) {
        // Free the existing buffer
        this.type.freeBuffer(this);

        // Create a new buffer with the new size
        this.createBuffer(newSize);

        // Reset used bytes since it's a new buffer
        usedBytes = 0;
    }
}
