package net.vulkanmod.render.memory;

public record SubCopyCommand(long srcOffset, int dstOffset, int bufferSize) {
}
