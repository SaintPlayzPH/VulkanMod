package net.vulkanmod.render.chunk;

import net.minecraft.util.Mth;
import net.vulkanmod.render.chunk.buffer.DrawBuffers;
import net.vulkanmod.render.chunk.util.CircularIntList;
import net.vulkanmod.render.chunk.util.Util;
import org.joml.Vector3i;

public class ChunkAreaManager {
    static final int WIDTH = 8;
    static final int HEIGHT = 8;

    static final int AREA_SH_XZ = Util.flooredLog(WIDTH);
    static final int AREA_SH_Y = Util.flooredLog(HEIGHT);

    static final int SEC_SH = 4;
    static final int BLOCK_TO_AREA_SH_XZ = AREA_SH_XZ + SEC_SH;
    static final int BLOCK_TO_AREA_SH_Y = AREA_SH_Y + SEC_SH;

    public final int size;
    final int sectionGridWidth;
    final int xzSize;
    final int ySize;
    final int minHeight;
    final ChunkArea[] chunkAreasArr;

    int prevX;
    int prevZ;

    public ChunkAreaManager(int width, int height, int minHeight) {
        this.minHeight = minHeight;
        this.sectionGridWidth = width;

        int t = (width >> AREA_SH_XZ) + 2;
        int relativeHeight = height - (minHeight >> SEC_SH);
        this.ySize = (relativeHeight & 0x5) == 0 ? (relativeHeight >> AREA_SH_Y) : (relativeHeight >> AREA_SH_Y) + 1;

        // Ensure t is odd
        if ((t & 1) == 0) t++;
        this.xzSize = t;

        this.size = xzSize * ySize * xzSize;
        this.chunkAreasArr = new ChunkArea[size];

        for (int j = 0; j < this.xzSize; ++j) {
            for (int k = 0; k < this.ySize; ++k) {
                for (int l = 0; l < this.xzSize; ++l) {
                    int index = this.getAreaIndex(j, k, l);
                    Vector3i position = new Vector3i(j << BLOCK_TO_AREA_SH_XZ, k << BLOCK_TO_AREA_SH_Y, l << BLOCK_TO_AREA_SH_XZ);
                    this.chunkAreasArr[index] = new ChunkArea(index, position, minHeight);
                }
            }
        }

        this.prevX = Integer.MIN_VALUE;
        this.prevZ = Integer.MIN_VALUE;
    }

    public void repositionAreas(int secX, int secZ) {
        int xS = secX >> AREA_SH_XZ;
        int zS = secZ >> AREA_SH_XZ;

        int deltaX = Mth.clamp(xS - this.prevX, -this.xzSize, this.xzSize);
        int deltaZ = Mth.clamp(zS - this.prevZ, -this.xzSize, this.xzSize);

        int xStart = Math.floorMod(xS - this.xzSize / 2, this.xzSize);
        int zStart = Math.floorMod(zS - this.xzSize / 2, this.xzSize);

        CircularIntList xList = new CircularIntList(this.xzSize, xStart);
        CircularIntList zList = new CircularIntList(this.xzSize, zStart);

        CircularIntList.OwnIterator xIterator = xList.iterator();
        CircularIntList.OwnIterator zIterator = zList.iterator();

        int xRangeStart = deltaX >= 0 ? this.xzSize - deltaX : 0;
        int xRangeEnd = deltaX >= 0 ? this.xzSize - 1 : -deltaX - 1;
        int xComplStart = deltaX >= 0 ? 0 : xRangeEnd;
        int xComplEnd = deltaX >= 0 ? xRangeStart - 1 : this.xzSize - 1;

        int zRangeStart = deltaZ >= 0 ? this.xzSize - deltaZ : 0;
        int zRangeEnd = deltaZ >= 0 ? this.xzSize - 1 : -deltaZ - 1;

        CircularIntList.RangeIterator xRangeIterator = xList.rangeIterator(xRangeStart, xRangeEnd);
        CircularIntList.RangeIterator xComplIterator = xList.rangeIterator(xComplStart, xComplEnd);
        CircularIntList.RangeIterator zRangeIterator = zList.rangeIterator(zRangeStart, zRangeEnd);

        updateChunkAreas(xS, zS, xRangeIterator, zIterator, xRangeStart);
        updateChunkAreas(xS, zS, xComplIterator, zRangeIterator, xComplStart);

        this.prevX = xS;
        this.prevZ = zS;
    }

    private void updateChunkAreas(int xS, int zS, CircularIntList.RangeIterator xIterator, CircularIntList.OwnIterator zIterator, int xStart) {
        int xAbsChunkIndex = xS - this.xzSize / 2 + xStart;
        while (xIterator.hasNext()) {
            int xRelativeIndex = xIterator.next();
            int xPos = xAbsChunkIndex++ << BLOCK_TO_AREA_SH_XZ;

            zIterator.restart();
            int zAbsChunkIndex = zS - this.xzSize / 2;

            while (zIterator.hasNext()) {
                int zRelativeIndex = zIterator.next();
                int zPos = zAbsChunkIndex++ << BLOCK_TO_AREA_SH_XZ;

                for (int yRel = 0; yRel < this.ySize; ++yRel) {
                    int yPos = this.minHeight + (yRel << BLOCK_TO_AREA_SH_Y);
                    ChunkArea chunkArea = this.chunkAreasArr[this.getAreaIndex(xRelativeIndex, yRel, zRelativeIndex)];
                    chunkArea.setPosition(xPos, yPos, zPos);
                    chunkArea.releaseBuffers();
                }
            }
        }
    }

    public ChunkArea getChunkArea(RenderSection section, int x, int y, int z) {
        int areaX = x >> (AREA_SH_XZ + SEC_SH);
        int areaY = (y - this.minHeight) >> (AREA_SH_Y + SEC_SH);
        int areaZ = z >> (AREA_SH_XZ + SEC_SH);

        int xRel = Math.floorMod(areaX, this.xzSize);
        int zRel = Math.floorMod(areaZ, this.xzSize);

        return this.chunkAreasArr[this.getAreaIndex(xRel, areaY, zRel)];
    }

    public void updateFrustumVisibility(VFrustum frustum) {
        for (ChunkArea chunkArea : this.chunkAreasArr) {
            chunkArea.updateFrustum(frustum);
        }
    }

    public void resetQueues() {
        for (ChunkArea chunkArea : this.chunkAreasArr) {
            chunkArea.resetQueue();
        }
    }

    private int getAreaIndex(int x, int y, int z) {
        return (z * this.ySize + y) * this.xzSize + x;
    }

    public void releaseAllBuffers() {
        for (ChunkArea chunkArea : this.chunkAreasArr) {
            chunkArea.releaseBuffers();
        }
    }

    public String[] getStats() {
        long vbSize = 0, ibSize = 0, frag = 0;
        long vbUsed = 0, ibUsed = 0;
        int count = 0;

        for (ChunkArea chunkArea : this.chunkAreasArr) {
            DrawBuffers drawBuffers = chunkArea.drawBuffers;
            if (drawBuffers.isAllocated()) {
                vbSize += getTotalBufferSize(drawBuffers.getVertexBuffer(), drawBuffers.getVertexBuffers());
                vbUsed += getTotalBufferUsed(drawBuffers.getVertexBuffer(), drawBuffers.getVertexBuffers());
                frag += getTotalBufferFragmentation(drawBuffers.getVertexBuffer(), drawBuffers.getVertexBuffers());

                var indexBuffer = drawBuffers.getIndexBuffer();
                if (indexBuffer != null) {
                    ibSize += indexBuffer.getSize();
                    ibUsed += indexBuffer.getUsed();
                    frag += indexBuffer.fragmentation();
                }

                count++;
            }
        }

        return formatStats(vbSize, vbUsed, ibSize, ibUsed, frag, count);
    }

    private long getTotalBufferSize(Buffer vertexBuffer, Map<?, Buffer> vertexBuffers) {
        long size = 0;
        if (vertexBuffer != null) {
            size += vertexBuffer.getSize();
        } else {
            for (Buffer buffer : vertexBuffers.values()) {
                size += buffer.getSize();
            }
        }
        return size;
    }

    private long getTotalBufferUsed(Buffer vertexBuffer, Map<?, Buffer> vertexBuffers) {
        long used = 0;
        if (vertexBuffer != null) {
            used += vertexBuffer.getUsed();
        } else {
            for (Buffer buffer : vertexBuffers.values()) {
                used += buffer.getUsed();
            }
        }
        return used;
    }

    private long getTotalBufferFragmentation(Buffer vertexBuffer, Map<?, Buffer> vertexBuffers) {
        long frag = 0;
        if (vertexBuffer != null) {
            frag += vertexBuffer.fragmentation();
        } else {
            for (Buffer buffer : vertexBuffers.values()) {
                frag += buffer.fragmentation();
            }
        }
        return frag;
    }

    private String[] formatStats(long vbSize, long vbUsed, long ibSize, long ibUsed, long frag, int count) {
        // Convert sizes from bytes to megabytes
        vbSize /= 1024 * 1024;
        vbUsed /= 1024 * 1024;
        ibSize /= 1024 * 1024;
        ibUsed /= 1024 * 1024;
        frag /= 1024 * 1024;

        return new String[]{
            String.format("Vertex Buffers: %d/%d MB", vbUsed, vbSize),
            String.format("Index Buffers: %d/%d MB", ibUsed, ibSize),
            String.format("Allocations: %d Frag: %d MB", count, frag)
        };
    }
}
