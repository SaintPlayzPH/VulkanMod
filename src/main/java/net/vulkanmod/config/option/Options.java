package net.vulkanmod.config.option;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.video.VideoModeManager;
import net.vulkanmod.config.video.VideoModeSet;
import net.vulkanmod.config.gui.OptionBlock;
import net.vulkanmod.render.chunk.build.light.LightMode;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;

import java.util.stream.IntStream;

import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR;

public abstract class Options {
    static int pretransformFlags = Vulkan.getPretransformFlags();
    public static boolean fullscreenDirty = false;
    static Config config = Initializer.CONFIG;
    static Minecraft minecraft = Minecraft.getInstance();
    static Window window = minecraft.getWindow();
    static net.minecraft.client.Options minecraftOptions = minecraft.options;

    private static boolean isRunningOnCompatDevice() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("linux") || osName.contains("android");
    }

    private static final int minImageCount;
    private static final int maxImageCount;

    static {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(DeviceManager.physicalDevice, Vulkan.getSurface(), capabilities);
            minImageCount = capabilities.minImageCount();
            maxImageCount = Math.min(capabilities.maxImageCount(), 32);
        }
    }

    public static OptionBlock[] getVideoOpts() {
        var videoMode = config.videoMode;
        var videoModeSet = VideoModeManager.getFromVideoMode(videoMode);

        if (videoModeSet == null) {
            videoModeSet = VideoModeSet.getDummy();
            videoMode = videoModeSet.getVideoMode(-1);
        }

        VideoModeManager.selectedVideoMode = videoMode;
        var refreshRates = videoModeSet.getRefreshRates();

        CyclingOption<Integer> RefreshRate = (CyclingOption<Integer>) new CyclingOption<>(
                Component.translatable("vulkanmod.options.refreshRate"),
                refreshRates.toArray(new Integer[0]),
                (value) -> {
                    VideoModeManager.selectedVideoMode.refreshRate = value;
                    VideoModeManager.applySelectedVideoMode();

                    if (minecraftOptions.fullscreen().get())
                        fullscreenDirty = true;
                },
                () -> VideoModeManager.selectedVideoMode.refreshRate)
                .setTranslator(refreshRate -> Component.nullToEmpty(refreshRate.toString()));

        Option<VideoModeSet> resolutionOption = new CyclingOption<>(
                Component.translatable("options.fullscreen.resolution"),
                VideoModeManager.getVideoResolutions(),
                (value) -> {
                    VideoModeManager.selectedVideoMode = value.getVideoMode(RefreshRate.getNewValue());
                    VideoModeManager.applySelectedVideoMode();

                    if (minecraftOptions.fullscreen().get())
                        fullscreenDirty = true;
                },
                () -> {
                    var videoMode1 = VideoModeManager.selectedVideoMode;
                    var videoModeSet1 = VideoModeManager.getFromVideoMode(videoMode1);

                    if (videoModeSet1 == null) {
                        videoModeSet1 = VideoModeSet.getDummy();
                    }

                    return videoModeSet1;
                })
                .setTranslator(resolution -> Component.nullToEmpty(resolution.toString()));

        resolutionOption.setOnChange(() -> {
            var videoMode1 = resolutionOption.getNewValue();
            var refreshRates1 = videoMode1.getRefreshRates();
            RefreshRate.setValues(refreshRates1.toArray(new Integer[0]));
            RefreshRate.setNewValue(refreshRates1.get(refreshRates1.size() - 1));
        });

        return new OptionBlock[]{
                new OptionBlock("", new Option<?>[]{
                        resolutionOption,
                        RefreshRate,
                        new SwitchOption(Component.translatable("vulkanmod.options.windowedFullscreen"),
                                value -> {
                                    config.windowedFullscreen = value;
                                    fullscreenDirty = true;
                                },
                                () -> config.windowedFullscreen),
                        new SwitchOption(Component.translatable("options.fullscreen"),
                                value -> {
                                    minecraftOptions.fullscreen().set(value);
                                    fullscreenDirty = true;
                                },
                                () -> minecraftOptions.fullscreen().get()),
                        new RangeOption(Component.translatable("options.framerateLimit"),
                                10, 260, 10,
                                value -> Component.nullToEmpty(value == 260 ?
                                        Component.translatable("options.framerateLimit.max").getString() :
                                        String.valueOf(value)),
                                value -> {
                                    minecraftOptions.framerateLimit().set(value);
                                    window.setFramerateLimit(value);
                                },
                                () -> minecraftOptions.framerateLimit().get()),
                        new SwitchOption(Component.translatable("options.vsync"),
                                value -> {
                                    minecraftOptions.enableVsync().set(value);
                                    window.updateVsync(value);
                                    Renderer.scheduleSwapChainUpdate();
                                },
                                () -> minecraftOptions.enableVsync().get()),
                }),
                new OptionBlock("", new Option<?>[]{
                        new RangeOption(Component.translatable("options.guiScale"),
                                0, window.calculateScale(0, minecraft.isEnforceUnicode()), 1,
                                value -> {
                                    if (value == 0) return Component.translatable("options.guiScale.auto");
                                    return Component.literal(String.valueOf(value));
                                },
                                value -> {
                                    minecraftOptions.guiScale().set(value);
                                    minecraft.resizeDisplay();
                                },
                                () -> (minecraftOptions.guiScale().get())),
                        new RangeOption(Component.translatable("options.gamma"),
                                0, 100, 1,
                                value -> {
                                    if (value == 0) return Component.translatable("options.gamma.min");
                                    else if (value == 50) return Component.translatable("options.gamma.default");
                                    else if (value == 100) return Component.translatable("options.gamma.max");
                                    return Component.literal(String.valueOf(value));
                                },
                                value -> minecraftOptions.gamma().set(value * 0.01),
                                () -> (int) (minecraftOptions.gamma().get() * 100.0)),
                }),
                new OptionBlock("", new Option<?>[]{
                        new CyclingOption<>(Component.translatable("vulkanmod.options.presentMode"),
                                new Integer[]{1, 2},
                                value -> {
                                   config.presentMode = value;
                                   Renderer.scheduleSwapChainUpdate();
                                }, () -> config.presentMode)
                                .setTranslator(value -> {
                                    String t = switch (value) {
                                        case 1 -> "FIFO (VSync)";
                                        case 2 -> "Mailbox (FastSync)";
                                        default -> "FIFO (VSync)";
                                    };
                                    return Component.translatable(t);
                                })
                                .setTooltip(Component.translatable("vulkanmod.options.presentMode.tooltip")),
                        new SwitchOption(Component.translatable("options.viewBobbing"),
                                (value) -> minecraftOptions.bobView().set(value),
                                () -> minecraftOptions.bobView().get()),
                        new CyclingOption<>(Component.translatable("options.attackIndicator"),
                                AttackIndicatorStatus.values(),
                                value -> minecraftOptions.attackIndicator().set(value),
                                () -> minecraftOptions.attackIndicator().get())
                                .setTranslator(value -> Component.translatable(value.getKey())),
                        new SwitchOption(Component.translatable("options.autosaveIndicator"),
                                value -> minecraftOptions.showAutosaveIndicator().set(value),
                                () -> minecraftOptions.showAutosaveIndicator().get()),
                })
        };
    }

    public static OptionBlock[] getGraphicsOpts() {
        return new OptionBlock[]{
                new OptionBlock("", new Option<?>[]{
                        new RangeOption(Component.translatable("options.renderDistance"),
                                2, 32, 1,
                                (value) -> minecraftOptions.renderDistance().set(value),
                                () -> minecraftOptions.renderDistance().get()),
                        new RangeOption(Component.translatable("options.simulationDistance"),
                                5, 32, 1,
                                (value) -> minecraftOptions.simulationDistance().set(value),
                                () -> minecraftOptions.simulationDistance().get()),
                        new CyclingOption<>(Component.translatable("options.prioritizeChunkUpdates"),
                                PrioritizeChunkUpdates.values(),
                                value -> minecraftOptions.prioritizeChunkUpdates().set(value),
                                () -> minecraftOptions.prioritizeChunkUpdates().get())
                                .setTranslator(value -> Component.translatable(value.getKey())),
                }),
                new OptionBlock("", new Option<?>[]{
                        new CyclingOption<>(Component.translatable("options.graphics"),
                                new GraphicsStatus[]{GraphicsStatus.FAST, GraphicsStatus.FANCY},
                                value ->
                                {
                                    minecraftOptions.graphicsMode().set(value);
                                    minecraft.levelRenderer.allChanged();
                                },
                                () -> minecraftOptions.graphicsMode().get())
                                .setTranslator(graphicsMode -> Component.translatable(graphicsMode.getKey())),
                        new CyclingOption<>(Component.translatable("options.particles"),
                                new ParticleStatus[]{ParticleStatus.MINIMAL, ParticleStatus.DECREASED, ParticleStatus.ALL},
                                value -> minecraftOptions.particles().set(value),
                                () -> minecraftOptions.particles().get())
                                .setTranslator(particlesMode -> Component.translatable(particlesMode.getKey())),
                        new CyclingOption<>(Component.translatable("options.renderClouds"),
                                CloudStatus.values(),
                                value -> minecraftOptions.cloudStatus().set(value),
                                () -> minecraftOptions.cloudStatus().get())
                                .setTranslator(value -> Component.translatable(value.getKey())),
                        new CyclingOption<>(Component.translatable("options.ao"),
                                new Integer[]{LightMode.FLAT, LightMode.SMOOTH, LightMode.SUB_BLOCK},
                                (value) -> {
                                    if (value > LightMode.FLAT)
                                        minecraftOptions.ambientOcclusion().set(true);
                                    else
                                        minecraftOptions.ambientOcclusion().set(false);

                                    Initializer.CONFIG.ambientOcclusion = value;

                                    minecraft.levelRenderer.allChanged();
                                },
                                () -> Initializer.CONFIG.ambientOcclusion)
                                .setTranslator(value -> switch (value) {
                                    case LightMode.FLAT -> Component.translatable("options.off");
                                    case LightMode.SMOOTH -> Component.translatable("options.on");
                                    case LightMode.SUB_BLOCK -> Component.translatable("vulkanmod.options.ao.subBlock");
                                    default -> Component.translatable("vulkanmod.options.unknown");
                                })
                                .setTooltip(Component.translatable("vulkanmod.options.ao.subBlock.tooltip")),
                        new SwitchOption(Component.translatable("vulkanmod.options.fastLeavesFix"),
                                value -> {
                                    config.fastLeavesFix = value;
                                    minecraft.levelRenderer.allChanged();
                                },
                                () -> config.fastLeavesFix)
                                .setTooltip(Component.translatable("vulkanmod.options.fastLeavesFix.tooltip")),
                        new RangeOption(Component.translatable("options.biomeBlendRadius"),
                                0, 7, 1,
                                value -> {
                                    int v = value * 2 + 1;
                                    return Component.nullToEmpty("%d x %d".formatted(v, v));
                                },
                                (value) -> {
                                    minecraftOptions.biomeBlendRadius().set(value);
                                    minecraft.levelRenderer.allChanged();
                                },
                                () -> minecraftOptions.biomeBlendRadius().get()),
                }),
                new OptionBlock("", new Option<?>[]{
                        new SwitchOption(Component.translatable("options.entityShadows"),
                                value -> minecraftOptions.entityShadows().set(value),
                                () -> minecraftOptions.entityShadows().get()),
                        new RangeOption(Component.translatable("options.entityDistanceScaling"),
                                50, 500, 25,
                                value -> minecraftOptions.entityDistanceScaling().set(value * 0.01),
                                () -> minecraftOptions.entityDistanceScaling().get().intValue() * 100),
                        new SwitchOption(Component.translatable("vulkanmod.options.fixPostEffectBug"),
                                value -> {
                                    config.postEffectFix = value;
                                },
                                () -> config.postEffectFix)
                                .setTooltip(Component.translatable("vulkanmod.options.fixPostEffectBug.tooltip")),
                        new SwitchOption(Component.translatable("vulkanmod.options.renderSkyFog"),
                                value -> {
                                    config.renderSkyFog = value;
                                    Renderer.recompile = true;
                                },
                                () -> config.renderSkyFog)
                                .setTooltip(Component.translatable("vulkanmod.options.renderSkyFog.tooltip")),
                        new SwitchOption(Component.translatable("vulkanmod.options.renderCloudFog"),
                                value -> {
                                    config.renderCloudFog = value;
                                    Renderer.recompile = true;
                                },
                                () -> config.renderCloudFog)
                                .setTooltip(Component.translatable("vulkanmod.options.renderCloudFog.tooltip")),
                        new CyclingOption<>(Component.translatable("options.mipmapLevels"),
                                new Integer[]{0, 1, 2, 3, 4},
                                value -> {
                                    minecraftOptions.mipmapLevels().set(value);
                                    minecraft.updateMaxMipLevel(value);
                                    minecraft.delayTextureReload();
                                },
                                () -> minecraftOptions.mipmapLevels().get())
                                .setTranslator(value -> Component.nullToEmpty(value.toString()))
                })
        };
    }

    public static OptionBlock[] getOptimizationOpts() {
        return new OptionBlock[] {
                new OptionBlock("", new Option[] {
                        new CyclingOption<>(Component.translatable("vulkanmod.options.advCulling"),
                                new Integer[]{1, 2, 3, 10},
                                value -> config.advCulling = value,
                                () -> config.advCulling)
                                .setTranslator(value -> {
                                    String t = switch (value) {
                                        case 1 -> "vulkanmod.options.advCulling.aggressive";
                                        case 2 -> "vulkanmod.options.advCulling.normal";
                                        case 3 -> "vulkanmod.options.advCulling.conservative";
                                        case 10 -> "options.off";
                                        default -> "vulkanmod.options.unknown";
                                    };
                                    return Component.translatable(t);
                                })
                                .setTooltip(Component.translatable("vulkanmod.options.advCulling.tooltip")),
                        new SwitchOption(Component.translatable("vulkanmod.options.animations"),
                                value -> {
                                    config.animations = value;
                                },
                                () -> config.animations)
                                .setTooltip(Component.translatable("vulkanmod.options.animations.tooltip")),
                        new SwitchOption(Component.translatable("vulkanmod.options.renderSky"),
                                value -> {
                                    config.renderSky = value;
                                },
                                () -> config.renderSky)
                                .setTooltip(Component.translatable("vulkanmod.options.renderSky.tooltip")),
                        new SwitchOption(Component.translatable("vulkanmod.options.renderFog"),
                                value -> {
                                    config.renderFog = value;
                                    Renderer.recompile = true;
                                },
                                () -> config.renderFog)
                                .setTooltip(Component.translatable("vulkanmod.options.renderFog.tooltip")),
                        new SwitchOption(Component.translatable("vulkanmod.options.renderEntityOutline"),
                                value -> {
                                    config.entityOutline = value;
                                },
                                () -> config.entityOutline)
                                .setTooltip(Component.translatable("vulkanmod.options.renderEntityOutline.tooltip")),
                        new SwitchOption(Component.translatable("vulkanmod.options.perRenderTypeAreaBuffers"),
                                value -> {
                                    config.perRenderTypeAreaBuffers = value;
                                    minecraft.levelRenderer.allChanged();
                                },
                                () -> config.perRenderTypeAreaBuffers)
                                .setTooltip(Component.translatable("vulkanmod.options.perRenderTypeAreaBuffers.tooltip")),
                        new SwitchOption(Component.translatable("vulkanmod.options.depthWrite"),
                                value -> {
                                    config.depthWrite = value;
                                    minecraft.levelRenderer.allChanged();
                                },
                                () -> config.depthWrite)
                                .setTooltip(Component.translatable("vulkanmod.options.depthWrite.tooltip")),
                        new SwitchOption(Component.translatable("vulkanmod.options.gaussianSkyBlending"),
                                value -> {
                                    config.gaussianSkyBlending = value;
                                },
                                () -> config.gaussianSkyBlending)
                                .setTooltip(Component.translatable("vulkanmod.options.gaussianSkyBlending.tooltip")),
                        new SwitchOption(Component.translatable("vulkanmod.options.excludeSampledUsage"),
                                value -> {
                                    config.dontUseImageSampled = value;
                                },
                                () -> config.dontUseImageSampled)
                                .setTooltip(Component.translatable("vulkanmod.options.excludeSampledUsage.tooltip")
                                    .append("\n\n")
                                    .append(Component.translatable("vulkanmod.options.excludeSampledUsage.warn"))),
                        new SwitchOption(Component.translatable("vulkanmod.options.entityCulling"),
                                value -> config.entityCulling = value,
                                () -> config.entityCulling)
                                .setTooltip(Component.translatable("vulkanmod.options.entityCulling.tooltip")),
                        new SwitchOption(Component.translatable("vulkanmod.options.indirectDraw"),
                                value -> config.indirectDraw = value,
                                () -> config.indirectDraw)
                                .setTooltip(Component.translatable("vulkanmod.options.indirectDraw.tooltip"))
                })
        };
    }

    public static OptionBlock[] getOtherOpts() {
    return new OptionBlock[] {
        new OptionBlock("", new Option[] {
            new RangeOption(Component.translatable("vulkanmod.options.frameQueue"),
                    1, 8, 1,
                    value -> {
                        config.frameQueueSize = value;
                        Renderer.scheduleSwapChainUpdate();
                    }, () -> config.frameQueueSize)
                    .setTooltip(Component.translatable("vulkanmod.options.frameQueue.tooltip")),
            new RangeOption(Component.translatable("vulkanmod.options.swapchainImages"), minImageCount,
                    maxImageCount, 1,
                    value -> {
                        config.imageCount = value;
                        Renderer.scheduleSwapChainUpdate();
                    }, () -> config.imageCount)
                    .setTooltip(Component.translatable("vulkanmod.options.swapchainImages.tooltip")),
            new SwitchOption(Component.translatable("vulkanmod.options.showPhoneRAMInfo"),
                    value -> config.showAndroidRAM = isRunningOnCompatDevice() ? value : false,
                    () -> isRunningOnPhone() && config.showAndroidRAM)
                    .setTooltip(
                    Component.translatable("vulkanmod.options.runningOnPhone")
                            .append(Component.literal(isRunningOnCompatDevice() ? "§aYes§r" : "§cNo§r"))
                            .append("\n\n")
                            .append(Component.translatable("vulkanmod.options.showPhoneRAMInfo.tooltip"))),
            new RangeOption(Component.translatable("vulkanmod.options.phoneRAMInfoUpdateDelay"), 0, 10, 1,
                    value -> {
                        if (value == 0) return Component.translatable("0.01s");
                        else if (value == 1) return Component.translatable("0.1s");
                        else if (value == 2) return Component.translatable("0.2s");
                        else if (value == 3) return Component.translatable("0.3s");
                        else if (value == 4) return Component.translatable("0.4s");
                        else if (value == 5) return Component.translatable("0.5s");
                        else if (value == 6) return Component.translatable("0.6s");
                        else if (value == 7) return Component.translatable("0.7s");
                        else if (value == 8) return Component.translatable("0.8s");
                        else if (value == 9) return Component.translatable("0.9s");
                        else if (value == 10) return Component.translatable("1s");
                        return Component.literal(String.valueOf(value));
                    },
                    value -> config.ramInfoUpdate = value,
                    () -> config.ramInfoUpdate)
                    .setTooltip(Component.translatable("vulkanmod.options.phoneRAMInfoUpdateDelay.tooltip")),
            new SwitchOption(Component.translatable("vulkanmod.options.showPojavInfo"),
                    value -> config.pojavInfo = value,
                    () -> config.pojavInfo)
                    .setTooltip(Component.translatable("vulkanmod.options.showPojavInfo.tooltip")),
            new SwitchOption(Component.translatable("vulkanmod.options.showLowRAM"),
                    value -> config.showlowRAM = value,
                    () -> config.showlowRAM)
                    .setTooltip(Component.translatable("vulkanmod.options.showLowRAM.tooltip")),
            new SwitchOption(Component.translatable("vulkanmod.options.resetHighUsageRec"),
                    value -> config.resetHighUsageRec = value,
                    () -> config.resetHighUsageRec)
                    .setTooltip(Component.translatable("vulkanmod.options.resetHighUsageRec.tooltip")),
            new CyclingOption<>(Component.translatable("vulkanmod.options.deviceSelector"),
                    IntStream.range(-1, DeviceManager.suitableDevices.size()).boxed().toArray(Integer[]::new),
                    value -> config.device = value,
                    () -> config.device)
                    .setTranslator(value -> {
                        String t;

                        if (value == -1)
                            t = "options.guiScale.auto";
                        else
                            t = DeviceManager.suitableDevices.get(value).deviceName;

                        return Component.translatable(t);
                    })
                    .setTooltip(
                            Component.nullToEmpty("%s: %s".formatted(
                                    Component.translatable("vulkanmod.options.deviceSelector.tooltip").getString(),
                                    DeviceManager.device.deviceName
                            )))
            })
        };
    }
}
