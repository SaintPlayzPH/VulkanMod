package net.vulkanmod.render.chunk.build.task;

import com.google.common.collect.Lists;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CompiledSection {
    public static final CompiledSection UNCOMPILED = new CompiledSection();
    final List<BlockEntity> blockEntities = Lists.newArrayList();
    boolean isCompletelyEmpty = false;
    @Nullable
    TerrainBufferBuilder.SortState transparencyState;

    public boolean hasTransparencyState() {
        return this.transparencyState != null;
    }

    public List<BlockEntity> getBlockEntities() {
        return this.blockEntities;
    }
}


