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
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.ArrayList;
import java.util.Collections;
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
        return 0;
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
        boolean proc = false;
        
        if (isRunningOnAndroid()) {
            proc = true;
        }

        strings.add(String.format("Java: %s %dbit", System.getProperty("java.version"), this.minecraft.is64Bit() ? 64 : 32));
        strings.add(String.format("Mem: % 2d%% %03d/%03dMB", usedMemory * 100L / maxMemory, bytesToMegabytes(usedMemory), bytesToMegabytes(maxMemory)));
        strings.add(String.format("Allocated: % 2d%% %03dMB", totalMemory * 100L / maxMemory, bytesToMegabytes(totalMemory)));
        strings.add(String.format("Off-heap: " + getOffHeapMemory() + "MB"));
        strings.add("BARMemory: " + MemoryType.BAR_MEM.usedBytes()+"/" + MemoryType.BAR_MEM.maxSize() + "MB");
        strings.add("DeviceMemory: " + MemoryType.GPU_MEM.usedBytes()+"/" + MemoryType.GPU_MEM.maxSize() + "MB");
        strings.add("");
        strings.add("VulkanMod " + getVersion());
        strings.add("CPU: " + SystemInfo.cpuInfo + (proc ? " (Processor)" : ""));
        strings.add("GPU: " + Vulkan.getDevice().deviceName);
        strings.add("Driver: " + Vulkan.getDevice().driverVersion);
        strings.add("Loader: " + Vulkan.getDevice().vkInstanceLoaderVersion);
        strings.add("Vulkan: " + Vulkan.getDevice().vkDriverVersion);
        strings.add("");
        Collections.addAll(strings, WorldRenderer.getInstance().getChunkAreaManager().getStats());
        strings.add("");
        strings.add("\u0056\u0075\u006c\u006b\u0061\u006e\u004d\u006f\u0064\u0020\u004d\u006f\u0064\u0069\u0066\u0069\u0065\u0064\u0020\u0042\u0079\u003a\u0020\u00a7\u0065\u0053\u0061\u0069\u006e\u0074\u0050\u006c\u0061\u0079\u007a\u0050\u0048\u00a7\u0072");
        if (isRunningOnAndroid() && Initializer.CONFIG.pojavInfo) {
            strings.add("");
            strings.add("Running on Pojav: §aYes§r");
        } else if (!isRunningOnAndroid() && Initializer.CONFIG.pojavInfo) {
            strings.add("");
            strings.add("Running on Pojav: §cNo§r");
        }
        if (isRunningOnAndroid() && Initializer.CONFIG.pojavInfo) {
            if (pretransformFlags == VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR || pretransformFlags == VK_SURFACE_TRANSFORM_ROTATE_270_BIT_KHR) {
                strings.add("Using Alternate Surface Rendering: §aYes§r");
                strings.add("This can cause rendering bugs/glitches");
            } else {
                strings.add("Using Alternate Surface Rendering: §cNo§r");
            }
        }
        if (isRunningOnAndroid() && Initializer.CONFIG.showAndroidRAM) {
            strings.add("");
            strings.add("Phone RAM Info:");
            strings.add(AndroidRAMInfo.getMemoryInfo());
            strings.add(AndroidRAMInfo.getAvailableMemoryInfo());
            strings.add(AndroidRAMInfo.getBuffersInfo());
        }
        
        return strings;
    }
    
    private static boolean isRunningOnAndroid() {
        if (System.getenv("\u0050\u004F\u004A\u0041\u0056\u005F\u0052\u0045\u004E\u0044\u0045\u0052") != null) {
            return true;
        } else {
            return false;
        }
    }

    private long getOffHeapMemory() {
        return bytesToMegabytes(ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed());
    }

//    /**
//     * @author
//     */
//    @Overwrite
//    public void drawGameInformation(PoseStack matrices) {
//        List<String> list = this.getGameInformation();
//        list.add("");
//        boolean bl = this.minecraft.getSingleplayerServer() != null;
//        list.add("Debug: Pie [shift]: " + (this.minecraft.options.renderDebugCharts ? "visible" : "hidden") + (bl ? " FPS + TPS" : " FPS") + " [alt]: " + (this.minecraft.options.renderFpsChart ? "visible" : "hidden"));
//        list.add("For help: press F3 + Q");
//
//        RenderSystem.enableBlend();
//        RenderSystem.setShader(GameRenderer::getPositionColorShader);
//        GuiBatchRenderer.beginBatch(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
//
//        for (int i = 0; i < list.size(); ++i) {
//            String string = list.get(i);
//            if (Strings.isNullOrEmpty(string)) continue;
//            int j = this.font.lineHeight;
//            int k = this.font.width(string);
//            int l = 2;
//            int m = 2 + j * i;
//
//            GuiBatchRenderer.fill(matrices, 1, m - 1, 2 + k + 1, m + j - 1, -1873784752);
//        }
//        GuiBatchRenderer.endBatch();
//
//        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
//        for (int i = 0; i < list.size(); ++i) {
//            String string = list.get(i);
//            if (Strings.isNullOrEmpty(string)) continue;
//            int j = this.font.lineHeight;
//            int k = this.font.width(string);
//            int l = 2;
//            int m = 2 + j * i;
//
//            GuiBatchRenderer.drawString(this.font, bufferSource, matrices, string, 2.0f, (float)m, 0xE0E0E0);
//        }
//        bufferSource.endBatch();
//    }

//    @Inject(method = "drawGameInformation",
//            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z",
//                    shift = At.Shift.AFTER,
//                    ordinal = 2))
//    protected void inject1(GuiGraphics guiGraphics, CallbackInfo ci)
//    {
//
//        RenderSystem.enableBlend();
//        RenderSystem.setShader(GameRenderer::getPositionColorShader);
//        GuiBatchRenderer.beginBatch(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
//    }
//
//
//    @Redirect(method = "renderLines",
//            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
//    protected void redirectFill(GuiGraphics instance, int i, int j, int k, int l, int m)
//    {
//        GuiBatchRenderer.fill(instance.pose(), m, k, j, l, m);
//    }
//
//    @Redirect(method = "drawGameInformation(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
//            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;draw(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I"))
//    protected int renderStuffRedirectThree(Font instance, PoseStack $$0, String $$1, float $$2, float $$3, int $$4)
//    {
//        return 0;
//    }
//
//    @Inject(method = "drawGameInformation(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"),
//            locals = LocalCapture.CAPTURE_FAILHARD)
//    public void renderStuff3(PoseStack poseStack, CallbackInfo ci, List<String> list)
//    {
//        GuiBatchRenderer.endBatch();
//
//        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
//        for (int i = 0; i < list.size(); ++i) {
//            String string = list.get(i);
//            if (Strings.isNullOrEmpty(string)) continue;
//            int j = this.font.lineHeight;
//            int k = this.font.width(string);
//            int l = 2;
//            int m = 2 + j * i;
//
//            GuiBatchRenderer.drawString(this.font, bufferSource, poseStack, string, 2.0f, (float)m, 0xE0E0E0);
//        }
//        bufferSource.endBatch();
//    }
//
//    /**
//     * @author
//     */
//    @Overwrite
//    public void drawSystemInformation(PoseStack matrices) {
//        List<String> list = this.getSystemInformation();
//
//        RenderSystem.enableBlend();
//        RenderSystem.setShader(GameRenderer::getPositionColorShader);
//        GuiBatchRenderer.beginBatch(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
//
//        for (int i = 0; i < list.size(); ++i) {
//            String string = list.get(i);
//            if (Strings.isNullOrEmpty(string)) continue;
//            int j = this.font.lineHeight;
//            int k = this.font.width(string);
//            int l = this.minecraft.getWindow().getGuiScaledWidth() - 2 - k;
//            int m = 2 + j * i;
//
//            GuiBatchRenderer.fill(matrices, l - 1, m - 1, l + k + 1, m + j - 1, -1873784752);
//        }
//        GuiBatchRenderer.endBatch();
//
//        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
//        for (int i = 0; i < list.size(); ++i) {
//            String string = list.get(i);
//            if (Strings.isNullOrEmpty(string)) continue;
//            int j = this.font.lineHeight;
//            int k = this.font.width(string);
//            int l = this.minecraft.getWindow().getGuiScaledWidth() - 2 - k;
//            int m = 2 + j * i;
//
//            GuiBatchRenderer.drawString(this.font, bufferSource, matrices, string, (float)l, (float)m, 0xE0E0E0);
//        }
//        bufferSource.endBatch();
//    }
}
