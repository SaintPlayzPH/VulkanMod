package net.vulkanmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.Platform;
import net.vulkanmod.config.video.VideoModeManager;
import net.vulkanmod.vulkan.Queue.QueueFamilyIndices;
import net.vulkanmod.vulkan.DeviceRAMInfo;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.SystemInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class Initializer implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("VulkanMod");

    private static String VERSION;
    private static boolean isGraphicsAndPresentSuitable = QueueFamilyIndices.isSuitable();
    public static Config CONFIG;
    public static boolean loggedDevice = false;

    // Static block to ensure CONFIG is initialized early
    static {
        initializeConfig();
    }

    @Override
    public void onInitializeClient() {
        if (System.getenv("POJAV_ENVIRON") != null) {
            LOGGER.info("=> We're running on PojavLauncher! <=");
        }
        if (System.getenv("SCL_ENVIRON") != null) {
            LOGGER.info("=> We're running on SolCraftLauncher! <=");
        }
        VERSION = FabricLoader.getInstance()
                .getModContainer("vulkanmod")
                .get()
                .getMetadata()
                .getVersion().getFriendlyString();

        LOGGER.info("==> VulkanMod <==");
        if (isRunningOnMobile() && !loggedDevice) {
            LOGGER.info("=• We're running on Mobile device! •=");
            LOGGER.info("• Phone Processor: " + SystemInfo.getProcessorNameForAndroidNoLog());
            LOGGER.info("• Phone RAM: " + DeviceRAMInfo.getRAMInfo());
            LOGGER.info("• Is Suitable: " + isGraphicsAndPresentSuitable);
            loggedDevice = true;
        }
        Renderer.recompile = true;
        LOGGER.info("==> Config Logger <===");
        LOGGER.info("Frame Queue Size: " + CONFIG.frameQueueSize);
        LOGGER.info("Show Device RAM: " + CONFIG.showDeviceRAM);
        LOGGER.info("Show Pojav Info: " + CONFIG.pojavInfo);
        LOGGER.info("Advanced Culling: " + CONFIG.advCulling);
        LOGGER.info("Indirect Draw: " + CONFIG.indirectDraw);
        LOGGER.info("Low VRAM Mode: " + CONFIG.perRenderTypeAreaBuffers);
        LOGGER.info("Fast Leaves Fix: " + CONFIG.fastLeavesFix);
        LOGGER.info("Entity Culling: " + CONFIG.entityCulling);
        LOGGER.info("Animations: " + CONFIG.animations);
        LOGGER.info("Render Sky: " + CONFIG.renderSky);
        LOGGER.info("Render Sky Fog: " + CONFIG.renderSkyFog);
        LOGGER.info("Render Cloud Fog: " + CONFIG.renderCloudFog);
        LOGGER.info("Fix Post-effect Bug: " + CONFIG.postEffectFix);
        LOGGER.info("Render Fog: " + CONFIG.renderFog);
        LOGGER.info("Entity Outline: " + CONFIG.entityOutline);
        LOGGER.info("Exclude Sampled Usage: " + CONFIG.dontUseImageSampled);
        LOGGER.info("Reset Highest Usage Records: " + CONFIG.resetHighUsageRec);
        LOGGER.info("Show Low RAM Warning: " + CONFIG.showlowRAM);
        LOGGER.info("Faster Gaussian Sky Blending: " + CONFIG.gaussianSkyBlending);
        LOGGER.info("Disable Depth Write if Translucent: " + CONFIG.depthWrite);
        LOGGER.info("Force FIFO VSync: " + CONFIG.forceFIFOVsync);
        LOGGER.info("Present Mode: " + CONFIG.presentMode);
        LOGGER.info("Device RAM Info update delay: " + CONFIG.ramInfoUpdate);
        LOGGER.info("Swapchain Images: " + CONFIG.imageCount);
        LOGGER.info("Device: " + CONFIG.device);
        LOGGER.info("Ambient Occlusion: " + CONFIG.ambientOcclusion);
        Platform.init();
        VideoModeManager.init();
    }

    private static void initializeConfig() {
        var configPath = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("vulkanmod_settings.json");

        CONFIG = loadConfig(configPath);
    }

    private static Config loadConfig(Path path) {
        Config config = Config.load(path);

        if (config == null) {
            config = new Config();
            config.write();
        }

        return config;
    }

    private static boolean isRunningOnMobile() {
        return System.getenv("SCL_RENDERER") != null || System.getenv("POJAV_RENDERER") != null || System.getenv("POJAV_ENVIRON") != null || System.getenv("POJAV_ENVIRON") != null;
    }

    public static String getVersion() {
        return VERSION;
    }
}
