package net.vulkanmod.mixin.render.leaves;

import net.minecraft.block.*;
import net.minecraft.util.math.Direction;
import net.vulkanmod.Initializer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = MangroveRootsBlockM.class, priority = 1900)
public abstract class MixinMangroveRootsBlock extends Block {

    public MixinMangroveRootsBlock(Settings settings) {
        super(settings);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSideInvisible(BlockState state, BlockState neighborState, Direction offset) {
        if (Initializer.CONFIG.cullLeaves) {
            return neighborState.getBlock() instanceof MangroveRootsBlock;
        }
        else return false;
    }
}
