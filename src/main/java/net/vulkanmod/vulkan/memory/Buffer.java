package net.vulkanmod.vulkan.memory;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

public class Buffer {
    private long id;
    private long allocation;

    private int bufferSize;
    private int usedBytes;
    private int offset;

    private final MemoryType type;
    private final int usage;
    private final PointerBuffer data;

    public Buffer(int usage, MemoryType type) {
        this.usage = usage;
        this.type = type;
        this.data = type.mappable() ? MemoryUtil.memAllocPointer(1) : null;
    }

    public void createBuffer(int bufferSize) {
        this.type.createBuffer(this, bufferSize);

        if (this.type.mappable()) {
            MemoryManager.getInstance().map(this.allocation, this.data);
        }
    }

    public void freeBuffer() {
        this.type.freeBuffer(this);
        if (this.type.mappable() && this.data != null) {
            MemoryUtil.memFree(this.data);
        }
    }

    public void reset() {
        usedBytes = 0;
    }

    public long getAllocation() {
        return allocation;
    }

    public int getUsedBytes() {
        return usedBytes;
    }

    public int getOffset() {
        return offset;
    }

    public long getId() {
        return id;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    protected void setBufferSize(int size) {
        this.bufferSize = size;
    }

    protected void setId(long id) {
        this.id = id;
    }

    protected void setAllocation(long allocation) {
        this.allocation = allocation;
    }

    public BufferInfo getBufferInfo() {
        return new BufferInfo(this.id, this.allocation, this.bufferSize);
    }

    public record BufferInfo(long id, long allocation, int bufferSize) {}
}
