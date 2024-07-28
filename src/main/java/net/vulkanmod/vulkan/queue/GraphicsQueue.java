package net.vulkanmod.vulkan.queue;

import net.vulkanmod.vulkan.Synchronization;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public class GraphicsQueue extends Queue {
    public static GraphicsQueue INSTANCE;

    private static CommandPool.CommandBuffer currentCmdBuffer;

    public GraphicsQueue(MemoryStack stack, int familyIndex) {
        super(stack, familyIndex);
    }

    public void startRecording() {
        currentCmdBuffer = beginCommands();
    }

    public void endRecordingAndSubmit() {
        long fence = submitCommands(currentCmdBuffer);
        Synchronization.INSTANCE.addCommandBuffer(currentCmdBuffer);

        currentCmdBuffer = null;
    }

    public CommandPool.CommandBuffer getCommandBuffer() {
        if (currentCmdBuffer != null) {
            return currentCmdBuffer;
        } else {
            return beginCommands();
        }
    }

    public long endIfNeeded(CommandPool.CommandBuffer commandBuffer) {
        if (currentCmdBuffer != null) {
            return VK_NULL_HANDLE;
        } else {
            return submitCommands(commandBuffer);
        }
    }

}
