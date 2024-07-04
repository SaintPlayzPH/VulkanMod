package net.vulkanmod.vulkan.memory;

public record SubCopyCommand(long srcOffset, int dstOffset, int bufferSize) {
}
