package net.vulkanmod.vulkan.memory;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

public class StagingBuffer extends Buffer {

    public StagingBuffer(int bufferSize) {
        super(VK_BUFFER_USAGE_TRANSFER_SRC_BIT, MemoryType.BAR_MEM);
        this.createBuffer(bufferSize);
    }

    public void copyBuffer(int size, ByteBuffer byteBuffer) {
        if (size > this.bufferSize - this.usedBytes) {
            resizeBuffer(size);
        }

        ByteBuffer destBuffer = this.data.getByteBuffer(this.usedBytes, size);
        destBuffer.put(byteBuffer);
        destBuffer.flip();

        this.usedBytes += size;
    }

    private void resizeBuffer(int newSize) {
        MemoryManager.getInstance().addToFreeable(this);
        this.createBuffer(newSize);
    }
}
