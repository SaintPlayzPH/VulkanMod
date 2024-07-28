package net.vulkanmod.render.model.quad;

import net.minecraft.core.Direction;

import static net.vulkanmod.render.model.quad.ModelQuad.VERTEX_SIZE;

public class ModelQuadFlags {
    /**
     * Indicates that the quad does not fully cover the given face for the model.
     */
    public static final int IS_PARTIAL = 0b001;

    /**
     * Indicates that the quad is parallel to its light face.
     */
    public static final int IS_PARALLEL = 0b010;

    /**
     * Indicates that the quad is aligned to the block grid.
     * This flag is only set if {@link #IS_PARALLEL} is set.
     */
    public static final int IS_ALIGNED = 0b100;

    public static boolean contains(int flags, int mask) {
        return (flags & mask) != 0;
    }

    public static int getQuadFlags(int[] vertices, Direction face) {
        float minX = 32.0F;
        float minY = 32.0F;
        float minZ = 32.0F;

        float maxX = -32.0F;
        float maxY = -32.0F;
        float maxZ = -32.0F;

        for (int i = 0; i < 4; ++i) {
            float x = Float.intBitsToFloat(vertices[i * VERTEX_SIZE]);
            float y = Float.intBitsToFloat(vertices[i * VERTEX_SIZE + 1]);
            float z = Float.intBitsToFloat(vertices[i * VERTEX_SIZE + 2]);

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        boolean partial = switch (face.getAxis()) {
            case X -> minY >= 0.0001f || minZ >= 0.0001f || maxY <= 0.9999F || maxZ <= 0.9999F;
            case Y -> minX >= 0.0001f || minZ >= 0.0001f || maxX <= 0.9999F || maxZ <= 0.9999F;
            case Z -> minX >= 0.0001f || minY >= 0.0001f || maxX <= 0.9999F || maxY <= 0.9999F;
        };

        boolean parallel = switch (face.getAxis()) {
            case X -> minX == maxX;
            case Y -> minY == maxY;
            case Z -> minZ == maxZ;
        };

        boolean aligned = parallel && switch (face) {
            case DOWN -> minY < 0.0001f;
            case UP -> maxY > 0.9999F;
            case NORTH -> minZ < 0.0001f;
            case SOUTH -> maxZ > 0.9999F;
            case WEST -> minX < 0.0001f;
            case EAST -> maxX > 0.9999F;
        };

        int flags = 0;

        if (partial) {
            flags |= IS_PARTIAL;
        }

        if (parallel) {
            flags |= IS_PARALLEL;
        }

        if (aligned) {
            flags |= IS_ALIGNED;
        }

        return flags;
    }


}
