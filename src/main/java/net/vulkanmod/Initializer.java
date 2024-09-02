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

	@Override
	public void onInitializeClient() {

		VERSION = FabricLoader.getInstance()
				.getModContainer("vulkanmod")
				.get()
				.getMetadata()
				.getVersion().getFriendlyString();

		LOGGER.info("== VulkanMod ==");
		LOGGER.info("⚒️ Patched by: ShadowMC69 🎮");
		LOGGER.warn("⚠️ If you downloaded this from YouTubers/TikTokers, delete this file immediately as this may contain malware 99% ⚠️");
		LOGGER.warn("⚠️ You're on your own if you don't believe and we'll not help you in case of bugs/crashes! ⚠️");
		LOGGER.warn("⚠️ He only help those people who downloaded it on his official Discord (no free invite link) ⚠️");
		LOGGER.info("🎮 Game is launching..... 🎮");
		Platform.init();
		VideoModeManager.init();
		
		var configPath = FabricLoader.getInstance()
				.getConfigDir()
				.resolve("vulkanmod_settings.json");

		CONFIG = loadConfig(configPath);
	}

	private static Config loadConfig(Path path) {
		Config config = Config.load(path);

		if(config == null) {
			config = new Config();
			config.write();
		}

		return config;
	}

	public static String getVersion() {
		return VERSION;
	}
}
