package net.vulkanmod.render.chunk.build.light.smooth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.vulkanmod.render.chunk.build.light.LightPipeline;
import net.vulkanmod.render.chunk.build.light.data.LightDataAccess;
import net.vulkanmod.render.chunk.build.light.data.QuadLightData;
import net.vulkanmod.render.chunk.util.SimpleDirection;
import net.vulkanmod.render.model.quad.ModelQuadFlags;
import net.vulkanmod.render.model.quad.QuadView;

/**
 * Re-adapted Sodium's smooth light pipeline
 * <p>
 * A light pipeline which produces smooth interpolated lighting and ambient occlusion for model quads. This
 * implementation makes a number of improvements over vanilla's own "smooth lighting" option. In no particular order:
 * <p>
 * - Corner blocks are now selected from the correct set of neighbors above block faces (fixes MC-148689 and MC-12558)
 * - Shading issues caused by anisotropy are fixed by re-orientating quads to a consistent ordering (fixes MC-138211)
 * - Inset block faces are correctly shaded by their neighbors, fixing a number of problems with non-full blocks such as
 * grass paths (fixes MC-11783 and MC-108621)
 * - Blocks next to emissive blocks are too bright (MC-260989)
 * - Synchronization issues between the main render thread's light engine and chunk build worker threads are corrected
 * by copying light data alongside block states, fixing a number of inconsistencies in baked chunks (no open issue)
 * <p>
 * This implementation also includes a significant number of optimizations:
 * <p>
 * - Computed light data for a given block face is cached and re-used again when multiple quads exist for a given
 * facing, making complex block models less expensive to render
 * - The light data cache encodes as much information as possible into integer words to improve cache locality and
 * to eliminate the multiple array lookups that would otherwise be needed, significantly speeding up this section
 * - Block faces aligned to the block grid use a fast-path for mapping corner light values to vertices without expensive
 * interpolation or blending, speeding up most block renders
 * - Some critical code paths have been re-written to hit the JVM's happy path, allowing it to perform auto-vectorization
 * of the blend functions
 * - Information about a given model quad is cached to enable the light pipeline to make certain assumptions and skip
 * unnecessary computation
 */
public class SmoothLightPipeline implements LightPipeline {
    /**
     * The cache which light data will be accessed from.
     */
    private final LightDataAccess lightCache;

    /**
     * The cached face data for each side of a block, both inner and outer.
     */
    private final AoFaceData[] cachedFaceData = new AoFaceData[6 * 2];
    /**
     * A temporary array for storing the intermediary results of weight data for non-aligned face blending.
     */
    private final float[] weights = new float[4];
    /**
     * The position at which the cached face data was taken at.
     */
    private long cachedPos = Long.MIN_VALUE;

    public SmoothLightPipeline(LightDataAccess cache) {
        this.lightCache = cache;

        for (int i = 0; i < this.cachedFaceData.length; i++) {
            this.cachedFaceData[i] = new AoFaceData();
        }
    }

    private static float clamp(float v) {
        if (v < 0.0f) {
            return 0.0f;
        } else if (v > 1.0f) {
            return 1.0f;
        }

        return v;
    }

    private static int packLightMap(float sl, float bl) {
        return (((int) sl & 0xFF) << 16) | ((int) bl & 0xFF);
    }

    @Override
    public void calculate(QuadView quad, BlockPos pos, QuadLightData out, Direction cullFace, Direction lightFaceO, boolean shade) {
        this.updateCachedData(pos.asLong());

        int flags = quad.getFlags();

        SimpleDirection lightFace = SimpleDirection.of(lightFaceO);

        final AoNeighborInfo neighborInfo = AoNeighborInfo.get(lightFace);

        // If the model quad is aligned to the block's face and covers it entirely, we can take a fast path and directly
        // map the corner values onto this quad's vertices. This covers most situations during rendering and provides
        // a modest speed-up.
        // To match vanilla behavior, also treat the face as aligned if it is parallel and the block state is a full cube
        if ((flags & ModelQuadFlags.IS_ALIGNED) != 0 || ((flags & ModelQuadFlags.IS_PARALLEL) != 0 && LightDataAccess.unpackFC(this.lightCache.get(pos)))) {
            if ((flags & ModelQuadFlags.IS_PARTIAL) == 0) {
                this.applyAlignedFullFace(neighborInfo, pos, lightFace, out);
            } else {
                this.applyAlignedPartialFace(neighborInfo, quad, pos, lightFace, out);
            }
        } else if ((flags & ModelQuadFlags.IS_PARALLEL) != 0) {
            this.applyParallelFace(neighborInfo, quad, pos, lightFace, out);
        } else {
            this.applyNonParallelFace(neighborInfo, quad, pos, lightFace, out);
        }

        this.applySidedBrightness(out, lightFaceO, shade);
    }

    /**
     * Quickly calculates the light data for a full grid-aligned quad. This represents the most common case (outward
     * facing quads on a full-block model) and avoids interpolation between neighbors as each corner will only ever
     * have two contributing sides.
     * Flags: IS_ALIGNED, !IS_PARTIAL
     */
    private void applyAlignedFullFace(AoNeighborInfo neighborInfo, BlockPos pos, SimpleDirection dir, QuadLightData out) {
        AoFaceData faceData = this.getCachedFaceData(pos, dir, true);
        neighborInfo.copyLightValues(faceData.lm, faceData.ao, out.lm, out.br);
    }

    /**
     * Calculates the light data for a grid-aligned quad that does not cover the entire block volume's face.
     * Flags: IS_ALIGNED, IS_PARTIAL
     */
    private void applyAlignedPartialFace(AoNeighborInfo neighborInfo, QuadView quad, BlockPos pos, SimpleDirection dir, QuadLightData out) {
        for (int i = 0; i < 4; i++) {
            // Clamp the vertex positions to the block's boundaries to prevent weird errors in lighting
            float cx = clamp(quad.getX(i));
            float cy = clamp(quad.getY(i));
            float cz = clamp(quad.getZ(i));

            float[] weights = this.weights;
            neighborInfo.calculateCornerWeights(cx, cy, cz, weights);
            this.applyAlignedPartialFaceVertex(pos, dir, weights, i, out, true);
        }
    }

    /**
     * This method is the same as {@link #applyNonParallelFace(AoNeighborInfo, QuadView, BlockPos, SimpleDirection,
     * QuadLightData)} but with the check for a depth of approximately 0 removed. If the quad is parallel but not
     * aligned, all of its vertices will have the same depth and this depth must be approximately greater than 0,
     * meaning the check for 0 will always return false.
     * Flags: !IS_ALIGNED, IS_PARALLEL
     */
    private void applyParallelFace(AoNeighborInfo neighborInfo, QuadView quad, BlockPos pos, SimpleDirection dir, QuadLightData out) {
        for (int i = 0; i < 4; i++) {
            // Clamp the vertex positions to the block's boundaries to prevent weird errors in lighting
            float cx = clamp(quad.getX(i));
            float cy = clamp(quad.getY(i));
            float cz = clamp(quad.getZ(i));

            float[] weights = this.weights;
            neighborInfo.calculateCornerWeights(cx, cy, cz, weights);

            float depth = neighborInfo.getDepth(cx, cy, cz);

            // If the quad is approximately grid-aligned (not inset) to the other side of the block, avoid unnecessary
            // computation by treating it is as aligned
            if (Mth.equal(depth, 1.0F)) {
                this.applyAlignedPartialFaceVertex(pos, dir, weights, i, out, false);
            } else {
                // Blend the occlusion factor between the blocks directly beside this face and the blocks above it
                // based on how inset the face is. This fixes a few issues with blocks such as farmland and paths.
                this.applyInsetPartialFaceVertex(pos, dir, depth, 1.0f - depth, weights, i, out);
            }
        }
    }

    /**
     * Flags: !IS_ALIGNED, !IS_PARALLEL
     */
    private void applyNonParallelFace(AoNeighborInfo neighborInfo, QuadView quad, BlockPos pos, SimpleDirection dir, QuadLightData out) {
        for (int i = 0; i < 4; i++) {
            // Clamp the vertex positions to the block's boundaries to prevent weird errors in lighting
            float cx = clamp(quad.getX(i));
            float cy = clamp(quad.getY(i));
            float cz = clamp(quad.getZ(i));

            float[] weights = this.weights;
            neighborInfo.calculateCornerWeights(cx, cy, cz, weights);

            float depth = neighborInfo.getDepth(cx, cy, cz);

            // If the quad is approximately grid-aligned (not inset), avoid unnecessary computation by treating it is as aligned
            if (Mth.equal(depth, 0.0F)) {
                this.applyAlignedPartialFaceVertex(pos, dir, weights, i, out, true);
            } else if (Mth.equal(depth, 1.0F)) {
                this.applyAlignedPartialFaceVertex(pos, dir, weights, i, out, false);
            } else {
                // Blend the occlusion factor between the blocks directly beside this face and the blocks above it
                // based on how inset the face is. This fixes a few issues with blocks such as farmland and paths.
                this.applyInsetPartialFaceVertex(pos, dir, depth, 1.0f - depth, weights, i, out);
            }
        }
    }

    private void applyAlignedPartialFaceVertex(BlockPos pos, SimpleDirection dir, float[] w, int i, QuadLightData out, boolean offset) {
        AoFaceData faceData = this.getCachedFaceData(pos, dir, offset);

        if (!faceData.hasUnpackedLightData()) {
            faceData.unpackLightData();
        }

        float sl = faceData.getBlendedSkyLight(w);
        float bl = faceData.getBlendedBlockLight(w);
        float ao = faceData.getBlendedShade(w);

        out.br[i] = ao;
        out.lm[i] = packLightMap(sl, bl);
    }

    private void applyInsetPartialFaceVertex(BlockPos pos, SimpleDirection dir, float n1d, float n2d, float[] w, int i, QuadLightData out) {
        AoFaceData n1 = this.getCachedFaceData(pos, dir, false);

        if (!n1.hasUnpackedLightData()) {
            n1.unpackLightData();
        }

        AoFaceData n2 = this.getCachedFaceData(pos, dir, true);

        if (!n2.hasUnpackedLightData()) {
            n2.unpackLightData();
        }

        // Blend between the direct neighbors and above based on the passed weights
        float ao = (n1.getBlendedShade(w) * n1d) + (n2.getBlendedShade(w) * n2d);
        float sl = (n1.getBlendedSkyLight(w) * n1d) + (n2.getBlendedSkyLight(w) * n2d);
        float bl = (n1.getBlendedBlockLight(w) * n1d) + (n2.getBlendedBlockLight(w) * n2d);

        out.br[i] = ao;
        out.lm[i] = packLightMap(sl, bl);
    }

    private void applySidedBrightness(QuadLightData out, Direction face, boolean shade) {
        float brightness = this.lightCache.getWorld().getShade(face, shade);
        float[] br = out.br;

        for (int i = 0; i < br.length; i++) {
            br[i] *= brightness;
        }
    }

    private AoFaceData getCachedFaceData(BlockPos pos, SimpleDirection face, boolean offset) {
        AoFaceData data = this.cachedFaceData[offset ? face.ordinal() : face.ordinal() + 6];

        if (!data.hasLightData()) {
            data.initLightData(this.lightCache, pos, face, offset);
        }

        return data;
    }

    private void updateCachedData(long key) {
        if (this.cachedPos != key) {
            for (AoFaceData data : this.cachedFaceData) {
                data.reset();
            }

            this.cachedPos = key;
        }
    }

}