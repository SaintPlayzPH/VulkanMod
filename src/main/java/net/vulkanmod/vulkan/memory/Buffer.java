package net.vulkanmod.vulkan.memory;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

public class Buffer {
    protected long id;
    protected long allocation;

    protected int bufferSize;
    protected int usedBytes;
    protected int offset;

    protected MemoryType type;
    protected int usage;
    protected PointerBuffer data;

    protected Buffer(int usage, MemoryType type) {
        this.usage = usage;
        this.type = type;
        this.data = type.mappable() ? MemoryUtil.memAllocPointer(1) : null;
    }

    protected void createBuffer(int bufferSize) {
        this.type.createBuffer(this, bufferSize);

        if (this.type.mappable()) {
            MemoryManager.getInstance().Map(this.allocation, this.data);
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

    protected int getBufferSize() {
        return bufferSize;
    }

    public MemoryType getType() {
        return type;
    }

    protected void setBufferSize(int size) {
        this.bufferSize = size;
    }

    public void setId(long id) {
        this.id = id;
    }

    protected void setAllocation(long allocation) {
        this.allocation = allocation;
    }

    public void setUsedBytes(int usedBytes) {
        this.usedBytes = usedBytes;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public BufferInfo getBufferInfo() {
        return new BufferInfo(this.id, this.allocation, this.bufferSize);
    }

    public record BufferInfo(long id, long allocation, int bufferSize) {}
}
