package net.vulkanmod.mixin.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.vulkan.AndroidRAMInfo;
import net.vulkanmod.vulkan.SystemInfo;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.Device;
import net.vulkanmod.vulkan.memory.MemoryType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.KHRSurface.*;
import static net.vulkanmod.Initializer.getVersion;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayM {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private static long bytesToMegabytes(long bytes) {
        return bytes / (1024 * 1024);
    }

    @Shadow
    @Final
    private Font font;

    @Shadow
    protected abstract List<String> getGameInformation();

    @Shadow
    protected abstract List<String> getSystemInformation();

    @Redirect(method = "getSystemInformation", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Lists;newArrayList([Ljava/lang/Object;)Ljava/util/ArrayList;"))
        private ArrayList<String> redirectList(Object[] elements) {
        ArrayList<String> strings = new ArrayList<>();
        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        int pretransformFlags = Vulkan.getPretransformFlags();
        boolean isPojav = isRunningOnPojav();

        strings.add(String.format("Java: %s %dbit", System.getProperty("java.version"), this.minecraft.is64Bit() ? 64 : 32));
        strings.add(String.format("Mem: % 2d%% %03d/%03dMB", usedMemory * 100L / maxMemory, bytesToMegabytes(usedMemory), bytesToMegabytes(maxMemory)));
        strings.add(String.format("Allocated: % 2d%% %03dMB", totalMemory * 100L / maxMemory, bytesToMegabytes(totalMemory)));
        strings.add("Off-heap: " + getOffHeapMemory() + "MB");
        strings.add("BARMemory: " + MemoryType.BAR_MEM.usedBytes() + "/" + MemoryType.BAR_MEM.maxSize() + "MB");
        strings.add("DeviceMemory: " + MemoryType.GPU_MEM.usedBytes() + "/" + MemoryType.GPU_MEM.maxSize() + "MB");
        strings.add("");
        strings.add("VulkanMod " + getVersion());
        strings.add("CPU: " + SystemInfo.cpuInfo + (isPojav ? " (Processor)" : ""));
        strings.add("GPU: " + Vulkan.getDevice().deviceName);
        strings.add("Driver: " + Vulkan.getDevice().driverVersion);
        strings.add("Loader: " + Vulkan.getDevice().vkInstanceLoaderVersion);
        strings.add("Vulkan: " + Vulkan.getDevice().vkDriverVersion);
        strings.add("");
        Collections.addAll(strings, WorldRenderer.getInstance().getChunkAreaManager().getStats());
        strings.add("");
        strings.add("\u0056\u0075\u006c\u006b\u0061\u006e\u004d\u006f\u0064\u0020\u004d\u006f\u0064\u0069\u0066\u0069\u0065\u0064\u0020\u0042\u0079\u003a\u0020\u00a7\u0065\u0053\u0061\u0069\u006e\u0074\u0050\u006c\u0061\u0079\u007a\u0050\u0048\u00a7\u0072");

        if (Initializer.CONFIG.pojavInfo) {
            strings.add("");
            strings.add("Running on Pojav: " + (isPojav ? "§aYes§r" : "§cNo§r"));
            if (isPojav) {
                strings.add("Using Alternate Surface Rendering: " + 
                            ((pretransformFlags == VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR || pretransformFlags == VK_SURFACE_TRANSFORM_ROTATE_270_BIT_KHR) ? "§aYes§r" : "§cNo§r"));
            }
        }
        if (isPojav && Initializer.CONFIG.showAndroidRAM) {
            strings.add("");
            strings.add("Device RAM Info:");
            strings.add(AndroidRAMInfo.getMemoryInfo());
            strings.add(AndroidRAMInfo.getAvailableMemoryInfo());
            strings.add(AndroidRAMInfo.getMemoryUsagePerSecond());
            strings.add(AndroidRAMInfo.getHighestRAMUsage());
            strings.add(AndroidRAMInfo.getBuffersInfo());
            if (Initializer.CONFIG.showlowRAM) {
                strings.add(AndroidRAMInfo.getAvailableRAMWarn());
            }
        }
        
        return strings;
    }

    private static boolean isRunningOnPojav() {
        if (System.getenv("POJAV_ENVIRON") != null) { //PojavLauncher
            return true;
        }
        if (System.getenv("SCL_ENVIRON") != null) { //SolCraftLauncher
            return true;
        }
        return System.getenv("POJAV_RENDERER") != null;
    }

    private long getOffHeapMemory() {
        return bytesToMegabytes(ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed());
    }
}
