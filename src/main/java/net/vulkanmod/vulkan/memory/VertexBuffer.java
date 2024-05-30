package net.vulkanmod.vulkan.memory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

public class VertexBuffer extends Buffer {
    private static final int DEFAULT_INITIAL_SIZE = 1024; // Default initial size for the buffer
    private static final int MAX_BATCH_SIZE = 65536; // Maximum batch size for upload

    private final List<ByteBuffer> uploadBatch; // Batch for batched uploads

    public VertexBuffer(MemoryType type) {
        super(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, type);
        this.uploadBatch = new ArrayList<>();
    }

    public void copyToVertexBuffer(long vertexSize, long vertexCount, ByteBuffer byteBuffer) {
        int bufferSize = (int) (vertexSize * vertexCount);

        if (bufferSize > remainingCapacity()) {
            resizeBuffer(Math.max(bufferSize, this.bufferSize * 2)); // Resize if necessary
        }

        if (bufferSize <= MAX_BATCH_SIZE) {
            // Single upload for small buffers
            type.copyToBuffer(this, bufferSize, byteBuffer);
        } else {
            // Batched uploads for large buffers
            uploadBatch.add(byteBuffer.slice()); // Add a slice of the byte buffer to the upload batch
            if (uploadBatch.size() >= MAX_BATCH_SIZE / bufferSize) {
                uploadBatch(); // Upload the batch when it reaches maximum batch size
            }
        }

        offset = usedBytes;
        usedBytes += bufferSize;
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

    private void uploadBatch() {
        // Calculate the total size of the batched uploads
        int totalSize = uploadBatch.stream().mapToInt(ByteBuffer::remaining).sum();

        // Allocate a temporary buffer to hold the batched data
        ByteBuffer batchBuffer = ByteBuffer.allocate(totalSize);

        // Copy data from individual buffers into the batch buffer
        for (ByteBuffer buffer : uploadBatch) {
            batchBuffer.put(buffer);
        }

        // Upload the batched data to the vertex buffer
        type.copyToBuffer(this, totalSize, batchBuffer);

        // Clear the upload batch
        uploadBatch.clear();
    }
}
