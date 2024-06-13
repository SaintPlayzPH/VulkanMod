package net.vulkanmod.vulkan;

import net.minecraft.client.*;

public class Booleans {
    private static Minecraft minecraft = Minecraft.getInstance();
    private static net.minecraft.client.Options minecraftOptions = minecraft.options;

    // public static boolean fancyGraphics = Minecraft.useFancyGraphics();

    public static boolean isVsyncEnabled() {
        return minecraftOptions.enableVsync();
    }
}
