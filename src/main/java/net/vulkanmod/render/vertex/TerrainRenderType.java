package net.vulkanmod.render.vertex;

import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.interfaces.ExtendedRenderType;
import net.vulkanmod.vulkan.VRenderSystem;

import java.util.EnumSet;

public enum TerrainRenderType {
    SOLID(RenderType.solid(), 2097152 /*BIG_BUFFER_SIZE*/),
    CUTOUT_MIPPED(RenderType.cutoutMipped(), 131072 /*SMALL_BUFFER_SIZE*/),
    CUTOUT(RenderType.cutout(), 131072 /*SMALL_BUFFER_SIZE*/),
    TRANSLUCENT(RenderType.translucent(), 262144 /*MEDIUM_BUFFER_SIZE*/),
    TRIPWIRE(RenderType.tripwire(), 262144 /*MEDIUM_BUFFER_SIZE*/);

    public static final TerrainRenderType[] VALUES = TerrainRenderType.values();

    public static final EnumSet<TerrainRenderType> COMPACT_RENDER_TYPES = EnumSet.of(CUTOUT_MIPPED, TRANSLUCENT);
    public static final EnumSet<TerrainRenderType> ALL_RENDER_TYPES = EnumSet.allOf(TerrainRenderType.class);

    public final int maxSize;  //Not sure if this should be changed to UINT16_INDEX_MAX * vertexSize
    public final int initialSize; //Only used W/ Per RenderTy[e AreaBuffers
 
    TerrainRenderType(RenderType renderType, int initialSize) {

        this.maxSize = renderType.bufferSize();
        this.initialSize = initialSize;
    }

    public static TerrainRenderType get(RenderType renderType) {
        return ((ExtendedRenderType)renderType).getTerrainRenderType();
    }

    public static EnumSet<TerrainRenderType> getActiveLayers() {
        return Initializer.CONFIG.uniqueOpaqueLayer ? COMPACT_RENDER_TYPES : ALL_RENDER_TYPES;
    }

    public static TerrainRenderType get(String renderType) {
        return switch (renderType)
        {
            case "solid" -> SOLID;
            case "cutout_mipped" -> CUTOUT_MIPPED;
            case "cutout" -> CUTOUT;
            case "translucent" -> TRANSLUCENT;
            case "tripwire" -> TRIPWIRE;
            default -> throw new IllegalStateException("Unexpected value: " + renderType);
        };
    }
}
