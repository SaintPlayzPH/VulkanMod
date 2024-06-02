package net.vulkanmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.Platform;
import net.vulkanmod.config.video.VideoModeManager;
import net.vulkanmod.vulkan.AndroidRAMInfo;
import net.vulkanmod.vulkan.SystemInfo;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.Device;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class Initializer implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("VulkanMod");

    private static String VERSION;
    public static Config CONFIG;
    public static boolean loggedAndroid = false;

    // Static block to ensure CONFIG is initialized early
    static {
        initializeConfig();
    }

    @Override
    public void onInitializeClient() {
        VERSION = FabricLoader.getInstance()
                .getModContainer("vulkanmod")
                .get()
                .getMetadata()
                .getVersion().getFriendlyString();

        LOGGER.info("==> VulkanMod <==");
        if (isRunningOnMobile() && !loggedAndroid) {
            LOGGER.info("=• We're running on Mobile device! •=");
            LOGGER.info("• Phone Processor: " + SystemInfo.getProcessorNameForAndroid());
            LOGGER.info("• Phone GPU: " + Vulkan.getDevice().deviceName);
            LOGGER.info("• Phone RAM:" + AndroidRAMInfo.getRAMInfo() + " KB");
            loggedAndroid = true;
        }
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
        return System.getenv("POJAV_RENDERER") != null;
    }

    public static String getVersion() {
        return VERSION;
    }
}
