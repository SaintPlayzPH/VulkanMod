package net.vulkanmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.Platform;
import net.vulkanmod.config.video.VideoModeManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class Initializer implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("VulkanMod");

    private static String VERSION;
    public static Config CONFIG;

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

        LOGGER.info("== VulkanMod ==");

        Platform.init();
        VideoModeManager.init();

        // Detect Android
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        
        if (osName != null && osName.toLowerCase().contains("android")) {
            LOGGER.info("Running on Android!");
            LOGGER.info("Android version: " + osVersion);
        } else {
            LOGGER.info("Not running on Android.");
            LOGGER.info("Operating System: " + osName);
        }
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

        // Log the loaded configuration
        LOGGER.info("Loaded config: glowEffectFix = " + config.glowEffectFix);

        return config;
    }

    public static String getVersion() {
        return VERSION;
    }
    }
