package net.vulkanmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.Platform;
import net.vulkanmod.config.video.VideoModeManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.file.Path;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

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

        logVulkanExtensions();
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

    public static String getVersion() {
        return VERSION;
    }

    private void logVulkanExtensions() {
        if (!GLFW.glfwInit()) {
            LOGGER.error("Unable to initialize GLFW");
            return;
        }

        if (!GLFW.glfwVulkanSupported()) {
            LOGGER.error("Vulkan is not supported");
            return;
        }

        try (MemoryStack stack = stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8("Vulkan Extensions Logger"))
                    .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
                    .pEngineName(stack.UTF8("No Engine"))
                    .engineVersion(VK_MAKE_VERSION(1, 0, 0))
                    .apiVersion(VK_API_VERSION_1_1);

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo);

            PointerBuffer pInstance = stack.mallocPointer(1);
            if (vkCreateInstance(createInfo, null, pInstance) != VK_SUCCESS) {
                LOGGER.error("Failed to create Vulkan instance");
                return;
            }

            long instance = pInstance.get(0);

            IntBuffer extensionCount = stack.mallocInt(1);
            vkEnumerateInstanceExtensionProperties((String) null, extensionCount, null);

            VkExtensionProperties.Buffer extensions = VkExtensionProperties.malloc(extensionCount.get(0), stack);
            vkEnumerateInstanceExtensionProperties((String) null, extensionCount, extensions);

            LOGGER.info("Available Vulkan Extensions:");
            for (VkExtensionProperties extension : extensions) {
                LOGGER.info(extension.extensionNameString());
            }

            vkDestroyInstance(instance, null);
        }
    }
}
