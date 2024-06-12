package net.vulkanmod.render.chunk;

import net.minecraft.core.BlockPos;
import net.vulkanmod.render.chunk.buffer.DrawBuffers;
import net.vulkanmod.render.chunk.util.StaticQueue;
import org.joml.FrustumIntersection;
import org.joml.Vector3i;

import java.util.Arrays;

public class ChunkArea {
    private static final int FRUSTUM_SIZE = 64;
    private static final int CHUNK_WIDTH = 8 << 4; // 128
    private static final int HALF_WIDTH = CHUNK_WIDTH >> 1;
    private static final int QUARTER_WIDTH = HALF_WIDTH >> 1;

    public final int index;
    private final byte[] inFrustum = new byte[FRUSTUM_SIZE];

    final Vector3i position;

    DrawBuffers drawBuffers;

    // Help JIT optimizations by hardcoding the queue size to the max possible ChunkArea limit
    public final StaticQueue<RenderSection> sectionQueue = new StaticQueue<>(512);

    public ChunkArea(int index, Vector3i origin, int minHeight) {
        this.index = index;
        this.position = origin;
        this.drawBuffers = new DrawBuffers(index, origin, minHeight);
    }

    public void updateFrustum(VFrustum frustum) {
        int frustumResult = frustum.cubeInFrustum(
                position.x(), position.y(), position.z(),
                position.x() + CHUNK_WIDTH, position.y() + CHUNK_WIDTH, position.z() + CHUNK_WIDTH
        );

        if (frustumResult == FrustumIntersection.INTERSECT) {
            Arrays.fill(inFrustum, (byte) FrustumIntersection.INTERSECT); // Initialize entire array
            updateFrustumRecursive(frustum, position.x(), position.y(), position.z(), CHUNK_WIDTH, 0);
        } else {
            Arrays.fill(inFrustum, (byte) frustumResult);
        }
    }

    private void updateFrustumRecursive(VFrustum frustum, float xMin, float yMin, float zMin, int size, int startIdx) {
        if (size == QUARTER_WIDTH) {
            int idx = startIdx;
            for (int i = 0; i < 8; i++) {
               if (idx >= FRUSTUM_SIZE) {
                    break; // Exit the loop if idx exceeds the array size
                }
                inFrustum[idx++] = (byte) frustum.cubeInFrustum(
                        xMin + (i & 1) * QUARTER_WIDTH,
                        yMin + ((i >> 1) & 1) * QUARTER_WIDTH,
                        zMin + ((i >> 2) & 1) * QUARTER_WIDTH,
                        xMin + ((i & 1) + 1) * QUARTER_WIDTH,
                        yMin + (((i >> 1) & 1) + 1) * QUARTER_WIDTH,
                        zMin + (((i >> 2) & 1) + 1) * QUARTER_WIDTH
                );
            }
            return;
        }

        int halfSize = size >> 1;
        for (int i = 0; i < 8; i++) {
            float xSubMin = xMin + (i & 1) * halfSize;
            float ySubMin = yMin + ((i >> 1) & 1) * halfSize;
            float zSubMin = zMin + ((i >> 2) & 1) * halfSize;
            int idx = startIdx + (i << 3);

            if (idx >= FRUSTUM_SIZE) {
                continue; // Skip if idx exceeds the array size
            }

            int result = frustum.cubeInFrustum(
                    xSubMin, ySubMin, zSubMin,
                    xSubMin + halfSize, ySubMin + halfSize, zSubMin + halfSize
            );

            inFrustum[idx] = (byte) result;

            // Recursively update for each subdivision
            updateFrustumRecursive(frustum, xSubMin, ySubMin, zSubMin, halfSize, idx);
        }
    }

    public byte getFrustumIndex(BlockPos pos) {
        return getFrustumIndex(pos.getX(), pos.getY(), pos.getZ());
    }

    public byte getFrustumIndex(int x, int y, int z) {
        int dx = x - position.x;
        int dy = y - position.y;
        int dz = z - position.z;

        int i = (dx >> 6 << 5) + (dy >> 6 << 4) + (dz >> 6 << 3);

        int xSub = (dx >> 3) & 0b100;
        int ySub = (dy >> 4) & 0b10;
        int zSub = (dz >> 5) & 0b1;

        return (byte) (i + xSub + ySub + zSub);
    }

    public byte inFrustum(byte i) {
        return inFrustum[i];
    }

    public DrawBuffers getDrawBuffers() {
        return drawBuffers;
    }

    public void resetQueue() {
        sectionQueue.clear();
    }

    public void setPosition(int x, int y, int z) {
        position.set(x, y, z);
    }

    public void releaseBuffers() {
        drawBuffers.releaseBuffers();
    }
}
