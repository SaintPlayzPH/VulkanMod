package net.vulkanmod.vulkan.memory;

import net.vulkanmod.render.chunk.util.Util;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.system.libc.LibCString.nmemcpy;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

public class StagingBuffer extends Buffer {

    public StagingBuffer(int bufferSize) {
        super(VK_BUFFER_USAGE_TRANSFER_SRC_BIT, MemoryType.BAR_MEM);
        this.usedBytes = 0;
        this.offset = 0;
        this.createBuffer(bufferSize);
    }

    public void copyBuffer(int size, ByteBuffer byteBuffer) {
        ensureCapacity(size);
        nmemcpy(this.data.get(0) + this.usedBytes, MemoryUtil.memAddress(byteBuffer), size);
        updateUsage(size);
    }

    public void copyBuffer2(int size, long byteBuffer) {
        ensureCapacity(size);
        nmemcpy(this.data.get(0) + this.usedBytes, byteBuffer, size);
        updateUsage(size);
    }

    public void align(int alignment) {
        int alignedValue = Util.align(usedBytes, alignment);
        if (alignedValue > this.bufferSize) {
            resizeBuffer(Math.max(this.bufferSize * 2, alignedValue));
        }
        usedBytes = alignedValue;
    }

    private void ensureCapacity(int size) {
        if (size > this.bufferSize - this.usedBytes) {
            resizeBuffer(Math.max(this.bufferSize * 2, this.bufferSize + size));
        }
    }

    private void resizeBuffer(int newSize) {
        this.type.freeBuffer(this);
        this.createBuffer(newSize);
        System.out.println("Resized staging buffer to: " + newSize);
    }

    private void updateUsage(int size) {
        this.offset = this.usedBytes;
        this.usedBytes += size;
    }
}
