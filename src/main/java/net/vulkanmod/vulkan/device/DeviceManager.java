package net.vulkanmod.vulkan.device;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.Device;
import net.vulkanmod.vulkan.queue.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toSet;
import static net.vulkanmod.vulkan.queue.Queue.*;
import static net.vulkanmod.vulkan.util.VUtil.asPointerBuffer;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.VK10.*;

public abstract class DeviceManager {
    public static List<Device> availableDevices;
    public static List<Device> suitableDevices;

    public static VkPhysicalDevice physicalDevice;
    public static VkDevice vkDevice;

    public static Device device;

    public static VkPhysicalDeviceProperties deviceProperties;
    public static VkPhysicalDeviceMemoryProperties memoryProperties;

    public static SurfaceProperties surfaceProperties;

    public static void init(VkInstance instance) {
        try {
            DeviceManager.pickPhysicalDevice(instance);
            DeviceManager.createLogicalDevice();
        } catch (Exception e) {
            logUnsupportedExtensions();

            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    static List<Device> getAvailableDevices(VkInstance instance) {
        try (MemoryStack stack = stackPush()) {
            List<Device> devices = new ObjectArrayList<>();

            IntBuffer deviceCount = stack.ints(0);

            vkEnumeratePhysicalDevices(instance, deviceCount, null);

            if (deviceCount.get(0) == 0) {
                return List.of();
            }

            PointerBuffer ppPhysicalDevices = stack.mallocPointer(deviceCount.get(0));
            vkEnumeratePhysicalDevices(instance, deviceCount, ppPhysicalDevices);

            VkPhysicalDevice currentDevice;

            for (int i = 0; i < ppPhysicalDevices.capacity(); i++) {
                currentDevice = new VkPhysicalDevice(ppPhysicalDevices.get(i), instance);

                Device device = new Device(currentDevice);
                devices.add(device);
            }

            return devices;
        }
    }

    static void getSuitableDevices(VkInstance instance) {
        availableDevices = getAvailableDevices(instance);

        List<Device> devices = new ObjectArrayList<>();
        for (Device device : availableDevices) {
            if (isDeviceSuitable(device.physicalDevice)) {
                devices.add(device);
            }
        }

        suitableDevices = devices;
    }

    public static void pickPhysicalDevice(VkInstance instance) {
        getSuitableDevices(instance);

        if (suitableDevices.isEmpty()) {
            throw new IllegalStateException("No suitable devices found");
        }

        try (MemoryStack stack = stackPush()) {

            int deviceIdx = Initializer.CONFIG.device;
            if (deviceIdx >= 0 && deviceIdx < suitableDevices.size())
                DeviceManager.device = suitableDevices.get(deviceIdx);
            else {
                DeviceManager.device = autoPickDevice();
                Initializer.CONFIG.device = -1;
            }

            physicalDevice = DeviceManager.device.physicalDevice;

            // Get device properties
            deviceProperties = device.properties;

            memoryProperties = VkPhysicalDeviceMemoryProperties.malloc();
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);

            surfaceProperties = querySurfaceProperties(physicalDevice, stack);
        }
    }

    static Device autoPickDevice() {
        ArrayList<Device> integratedGPUs = new ArrayList<>();
        ArrayList<Device> otherDevices = new ArrayList<>();

        boolean flag = false;

        Device currentDevice = null;
        for (Device device : suitableDevices) {
            currentDevice = device;

            int deviceType = device.properties.deviceType();
            if (deviceType == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
                flag = true;
                break;
            } else if (deviceType == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU)
                integratedGPUs.add(device);
            else
                otherDevices.add(device);
        }

        if (!flag) {
            if (!integratedGPUs.isEmpty())
                currentDevice = integratedGPUs.get(0);
            else if (!otherDevices.isEmpty())
                currentDevice = otherDevices.get(0);
            else {
                throw new IllegalStateException("Failed to find a suitable GPU");
            }
        }

        return currentDevice;
    }

    public static void createLogicalDevice() {
        try (MemoryStack stack = stackPush()) {

            int[] uniqueQueueFamilies = QueueFamilyIndices.unique();

            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(uniqueQueueFamilies.length, stack);

            for (int i = 0; i < uniqueQueueFamilies.length; i++) {
                VkDeviceQueueCreateInfo queueCreateInfo = queueCreateInfos.get(i);
                queueCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
                queueCreateInfo.queueFamilyIndex(uniqueQueueFamilies[i]);
                queueCreateInfo.pQueuePriorities(stack.floats(1.0f));
            }

            VkPhysicalDeviceVulkan11Features deviceVulkan11Features = VkPhysicalDeviceVulkan11Features.calloc(stack);
            deviceVulkan11Features.sType$Default();
            deviceVulkan11Features.shaderDrawParameters(device.isDrawIndirectSupported());

            VkPhysicalDeviceFeatures2 deviceFeatures = VkPhysicalDeviceFeatures2.calloc(stack);
            deviceFeatures.sType$Default();
            deviceFeatures.features().samplerAnisotropy(device.availableFeatures.features().samplerAnisotropy());
            deviceFeatures.features().logicOp(device.availableFeatures.features().logicOp());
            // TODO: Disable indirect draw option if unsupported.
            deviceFeatures.features().multiDrawIndirect(device.isDrawIndirectSupported());

            // Must not set line width to anything other than 1.0 if this is not supported
            if (device.availableFeatures.features().wideLines()) {
                deviceFeatures.features().wideLines(true);
                VRenderSystem.canSetLineWidth = true;
            }

            deviceFeatures.pNext(deviceVulkan11Features);
            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack);
            createInfo.sType$Default();
            createInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
            createInfo.pQueueCreateInfos(queueCreateInfos);
            createInfo.pEnabledFeatures(deviceFeatures.features());
            createInfo.pNext(deviceVulkan11Features);

            if (Vulkan.DYNAMIC_RENDERING) {
                VkPhysicalDeviceDynamicRenderingFeaturesKHR dynamicRenderingFeaturesKHR = VkPhysicalDeviceDynamicRenderingFeaturesKHR.calloc(stack);
                dynamicRenderingFeaturesKHR.sType$Default();
                dynamicRenderingFeaturesKHR.dynamicRendering(true);

                deviceVulkan11Features.pNext(dynamicRenderingFeaturesKHR.address());
            }

            createInfo.ppEnabledExtensionNames(asPointerBuffer(Vulkan.REQUIRED_EXTENSION));

            createInfo.ppEnabledLayerNames(Vulkan.ENABLE_VALIDATION_LAYERS ? asPointerBuffer(Vulkan.VALIDATION_LAYERS) : null);

            PointerBuffer pDevice = stack.pointers(VK_NULL_HANDLE);

            int res = vkCreateDevice(physicalDevice, createInfo, null, pDevice);
            Vulkan.checkResult(res, "Failed to create logical device");

            vkDevice = new VkDevice(pDevice.get(0), physicalDevice, createInfo, getInstanceVersion());
        }
    }

    private static int getInstanceVersion() {
        return Device.instanceVersion;
    }

    private static PointerBuffer getRequiredExtensions() {
        PointerBuffer glfwExtensions = glfwGetRequiredInstanceExtensions();

        if (Vulkan.ENABLE_VALIDATION_LAYERS) {

            MemoryStack stack = stackGet();

            PointerBuffer extensions = stack.mallocPointer(glfwExtensions.capacity() + 1);

            extensions.put(glfwExtensions);
            extensions.put(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME));

            // Rewind the buffer before returning it to reset its position back to 0
            return extensions.rewind();
        }

        return glfwExtensions;
    }

    private static boolean isDeviceSuitable(VkPhysicalDevice device) {
        try (MemoryStack stack = stackPush()) {
            VkExtensionProperties.Buffer availableExtensions = getAvailableExtension(stack, device);
            boolean extensionsSupported = availableExtensions.stream()
                    .map(VkExtensionProperties::extensionNameString)
                    .collect(toSet())
                    .containsAll(Vulkan.REQUIRED_EXTENSION);

            boolean swapChainAdequate = false;

            if (extensionsSupported) {
                SurfaceProperties surfaceProperties = querySurfaceProperties(device, stack);
                swapChainAdequate = surfaceProperties.formats.hasRemaining() && surfaceProperties.presentModes.hasRemaining();
            }

            VkPhysicalDeviceFeatures supportedFeatures = VkPhysicalDeviceFeatures.malloc(stack);
            vkGetPhysicalDeviceFeatures(device, supportedFeatures);
            boolean anisotropicFilterSupported = supportedFeatures.samplerAnisotropy();

            return QueueFamilyIndices.findQueueFamilies(device) && extensionsSupported && swapChainAdequate;
        }
    }

    private static VkExtensionProperties.Buffer getAvailableExtension(MemoryStack stack, VkPhysicalDevice device) {
        IntBuffer extensionCount = stack.ints(0);
        vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, null);

        VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.malloc(extensionCount.get(0), stack);
        vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, availableExtensions);
        return availableExtensions;
    }

    private static SurfaceProperties querySurfaceProperties(VkPhysicalDevice device, MemoryStack stack) {
        SurfaceProperties properties = new SurfaceProperties();

        // Query Surface Formats
        IntBuffer formatCount = stack.ints(0);
        vkGetPhysicalDeviceSurfaceFormatsKHR(device, VRenderSystem.surface, formatCount, null);
        if (formatCount.get(0) != 0) {
            properties.formats = VkSurfaceFormatKHR.malloc(formatCount.get(0), stack);
            vkGetPhysicalDeviceSurfaceFormatsKHR(device, VRenderSystem.surface, formatCount, properties.formats);
        }

        // Query Present Modes
        IntBuffer presentModeCount = stack.ints(0);
        vkGetPhysicalDeviceSurfacePresentModesKHR(device, VRenderSystem.surface, presentModeCount, null);
        if (presentModeCount.get(0) != 0) {
            properties.presentModes = stack.mallocInt(presentModeCount.get(0));
            vkGetPhysicalDeviceSurfacePresentModesKHR(device, VRenderSystem.surface, presentModeCount, properties.presentModes);
        }

        return properties;
    }

    static void logUnsupportedExtensions() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n");

        if (availableDevices.isEmpty()) {
            stringBuilder.append("No available device found");
        }

        for (Device device : availableDevices) {
            stringBuilder.append("Device: %s\n".formatted(device.deviceName));

            var unsupported = device.getUnsupportedExtensions(Vulkan.REQUIRED_EXTENSION);
            if (unsupported.isEmpty()) {
                stringBuilder.append("All required extensions are supported\n");
            } else {
                stringBuilder.append("Unsupported extension: %s\n".formatted(unsupported));
            }
        }

        Initializer.LOGGER.info(stringBuilder.toString());
    }

    public static class SurfaceProperties {
        public VkSurfaceFormatKHR.Buffer formats;
        public IntBuffer presentModes;
    }
}
