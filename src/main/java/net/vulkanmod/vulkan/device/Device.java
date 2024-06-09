package net.vulkanmod.vulkan.device;

import net.vulkanmod.Initializer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.lwjgl.glfw.GLFW.GLFW_PLATFORM_WIN32;
import static org.lwjgl.glfw.GLFW.glfwGetPlatform;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.vkEnumerateInstanceVersion;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2;

public class Device {
    final VkPhysicalDevice physicalDevice;
    final VkPhysicalDeviceProperties properties;

    private final int vendorId;
    public final String vendorIdString;
    public final String deviceName;
    public final String driverVersion;
    public final String vkDriverVersion;
    public final String vkInstanceLoaderVersion;

    public final VkPhysicalDeviceFeatures2 availableFeatures;
    public final VkPhysicalDeviceVulkan11Features availableFeatures11;

    private boolean drawIndirectSupported;

    public Device(VkPhysicalDevice device) {
        this.physicalDevice = device;

        properties = VkPhysicalDeviceProperties.malloc();
        vkGetPhysicalDeviceProperties(physicalDevice, properties);

        this.vendorId = properties.vendorID();
        this.vendorIdString = decodeVendor(properties.vendorID());
        this.deviceName = properties.deviceNameString();
        this.driverVersion = decodeDvrVersion(properties.driverVersion(), properties.vendorID());
        this.vkDriverVersion = decDefVersion(properties.apiVersion());
        this.vkInstanceLoaderVersion = decDefVersion(getVkVer());
        
        this.availableFeatures = VkPhysicalDeviceFeatures2.calloc();
        this.availableFeatures.sType$Default();

        this.availableFeatures11 = VkPhysicalDeviceVulkan11Features.malloc();
        this.availableFeatures11.sType$Default();
        this.availableFeatures.pNext(this.availableFeatures11);

        vkGetPhysicalDeviceFeatures2(this.physicalDevice, this.availableFeatures);

        if (this.availableFeatures.features().multiDrawIndirect() && this.availableFeatures11.shaderDrawParameters())
            this.drawIndirectSupported = true;
    }

    private static String decodeVendor(int i) {
        return switch (i) {
            case (0x10DE) -> "Nvidia";
            case (0x1022) -> "AMD";
            case (0x8086) -> "Intel";
            default -> "undef"; //Either AMD or Unknown Driver version/vendor and.or Encoding Scheme
        };
    }

    static String decDefVersion(int v) {
        return VK_VERSION_MAJOR(v) + "." + VK_VERSION_MINOR(v) + "." + VK_VERSION_PATCH(v);
    }

    private static String decodeDvrVersion(int v, int i) {
        return switch (i) {
            case (0x10DE) -> decodeNvidia(v); //Nvidia
            case (0x1022) -> decDefVersion(v); //AMD
            case (0x8086) -> decIntelVersion(v); //Intel
            default -> decDefVersion(v); //Either AMD or Unknown Driver Encoding Scheme
        };
    }

    private static String decIntelVersion(int v) {
        return (glfwGetPlatform() == GLFW_PLATFORM_WIN32) ? (v >>> 14) + "." + (v & 0x3fff) : decDefVersion(v);
    }


    private static String decodeNvidia(int v) {
        return (v >>> 22 & 0x3FF) + "." + (v >>> 14 & 0xff) + "." + (v >>> 6 & 0xff) + "." + (v & 0xff);
    }

    static int getVkVer() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var a = stack.mallocInt(1);
            vkEnumerateInstanceVersion(a);
            int vkVer1 = a.get(0);
            if (VK_VERSION_MINOR(vkVer1) < 1) {
                throw new RuntimeException("Vulkan 1.1 not supported: Only Has: %s".formatted(decDefVersion(vkVer1)));
            }
            return vkVer1;
        }
    }

    public Set<String> getUnsupportedExtensions(Set<String> requiredExtensions) {
        try (MemoryStack stack = stackPush()) {

            IntBuffer extensionCount = stack.ints(0);

            vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, extensionCount, null);

            VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.malloc(extensionCount.get(0), stack);

            vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, extensionCount, availableExtensions);

            Set<String> extensions = availableExtensions.stream()
                    .map(VkExtensionProperties::extensionNameString)
                    .collect(toSet());

            Set<String> unsupportedExtensions = new HashSet<>(requiredExtensions);
            unsupportedExtensions.removeAll(extensions);

            return unsupportedExtensions;
        }
    }

    public boolean isDrawIndirectSupported() {
        return drawIndirectSupported;
    }

    public boolean isAMD() {
        return vendorId == 0x1022;
    }

    public boolean isNvidia() {
        return vendorId == 0x10DE;
    }

    public boolean isIntel() {
        return vendorId == 0x8086;
    }
}
