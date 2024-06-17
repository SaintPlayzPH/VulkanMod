package net.vulkanmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.Platform;
import net.vulkanmod.config.video.VideoModeManager;
import net.vulkanmod.vulkan.DeviceRAMInfo;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.SystemInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class Initializer implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("VulkanMod");

    private static String VERSION;
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
            LOGGER.info("• Phone Processor: " + SystemInfo.getProcessorNameForAndroid());
            LOGGER.info("• Phone RAM: " + DeviceRAMInfo.getRAMInfo());
            loggedDevice = true;
        }
        Platform.init();
        VideoModeManager.init();
        
        // Recompile Renderer and log config
        Renderer.recompile = true;
        logConfig(CONFIG);
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
        return System.getenv("POJAV_RENDERER") != null;
    }

    public static String getVersion() {
        return VERSION;
    }

    private static void logConfig(Config config) {
        LOGGER.info("==> Config Logger <===");
        LOGGER.info("Frame Queue Size: " + config.frameQueueSize);
        LOGGER.info("Show Device RAM: " + config.showDeviceRAM);
        LOGGER.info("Show Pojav Info: " + config.pojavInfo);
        LOGGER.info("Advanced Culling: " + config.advCulling);
        LOGGER.info("Indirect Draw: " + config.indirectDraw);
        LOGGER.info("Low VRAM Mode: " + config.perRenderTypeAreaBuffers);
        LOGGER.info("Fast Leaves Fix: " + config.fastLeavesFix);
        LOGGER.info("Entity Culling: " + config.entityCulling);
        LOGGER.info("Animations: " + config.animations);
        LOGGER.info("Render Sky: " + config.renderSky);
        LOGGER.info("Render Sky Fog: " + config.renderSkyFog);
        LOGGER.info("Render Cloud Fog: " + config.renderCloudFog);
        LOGGER.info("Fix Post-effect Bug: " + config.postEffectFix);
        LOGGER.info("Render Fog: " + config.renderFog);
        LOGGER.info("Entity Outline: " + config.entityOutline);
        LOGGER.info("Exclude Sampled Usage: " + config.dontUseImageSampled);
        LOGGER.info("Reset Highest Usage Records: " + config.resetHighUsageRec);
        LOGGER.info("Show Low RAM Warning: " + config.showlowRAM);
        LOGGER.info("Faster Gaussian Sky Blending: " + config.gaussianSkyBlending);
        LOGGER.info("Disable Depth Write if Translucent: " + config.depthWrite);
        LOGGER.info("Force FIFO VSync: " + config.forceFIFOVsync);
        LOGGER.info("Present Mode: " + config.presentMode);
        LOGGER.info("Device RAM Info update delay: " + config.ramInfoUpdate);
        LOGGER.info("Swapchain Images: " + config.imageCount);
        LOGGER.info("Device: " + config.device);
        LOGGER.info("Ambient Occlusion: " + config.ambientOcclusion);
    }
}
