package net.vulkanmod.mixin;

import net.vulkanmod.Initializer;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class MixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        // Initialization logic, if necessary
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Log the target class name and mixin class name for debugging
        Initializer.LOGGER.info("Evaluating mixin for target class: " + targetClassName + ", mixin class: " + mixinClassName);
        
        if (mixinClassName.equals("net.vulkanmod.mixin.compatibility.PostChainM") || 
            mixinClassName.equals("net.vulkanmod.mixin.compatibility.PostPassM")) {
            // Check if CONFIG is initialized and postEffect is true
            boolean shouldApply = Initializer.CONFIG != null && !Initializer.CONFIG.glowEffectFix;
            Initializer.LOGGER.info("Checking mixin " + mixinClassName + ": " + shouldApply);
            return shouldApply;
        }
        return true; // Apply all other mixins by default
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // No-op
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No-op
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No-op
    }
}
