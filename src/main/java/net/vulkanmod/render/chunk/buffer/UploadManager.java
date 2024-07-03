package net.vulkanmod.render.chunk.buffer;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.render.memory.SubCopyCommand;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;

import static net.vulkanmod.vulkan.queue.Queue.GraphicsQueue;
import static org.lwjgl.vulkan.VK10.*;

public class UploadManager {
    public static final int FRAME_NUM = 2;
    public static UploadManager INSTANCE;

    public static void createInstance() {
        INSTANCE = new UploadManager();
    }

    boolean hasBufferSwap = false;

    ObjectArrayList<Object>[] recordedUploads;
    CommandPool.CommandBuffer[] commandBuffers;

    Long2ObjectArrayMap<ObjectArrayFIFOQueue<SubCopyCommand>> subCopyCommands = new Long2ObjectArrayMap<>();

    int currentFrame;

    public void init() {
        this.commandBuffers = new CommandPool.CommandBuffer[FRAME_NUM];
        this.recordedUploads = new ObjectArrayList[FRAME_NUM];

        for (int i = 0; i < FRAME_NUM; i++) {
            this.recordedUploads[i] = new ObjectArrayList<>();
        }
    }

    public void swapBuffers(long srcBuffer, long dstBuffer) {
        hasBufferSwap = true;
        if (!this.subCopyCommands.containsKey(srcBuffer)) return;
        this.subCopyCommands.put(dstBuffer, this.subCopyCommands.remove(srcBuffer));
    }

    public synchronized void submitUploads() {
        if (subCopyCommands.isEmpty()) {
            return;
        }
        if (commandBuffers[currentFrame] == null)
            this.commandBuffers[currentFrame] = GraphicsQueue.beginCommands();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GraphicsQueue.PriorWriteBarrier(this.commandBuffers[currentFrame].getHandle());

            long stagingBufferId = Vulkan.getStagingBuffer().getId();
            for (long bufferHandle : subCopyCommands.keySet()) {
                final ObjectArrayFIFOQueue<SubCopyCommand> subCopyCommands1 = subCopyCommands.get(bufferHandle);

                int size = subCopyCommands1.size();
                VkBufferCopy.Buffer vkBufferCopies = VkBufferCopy.malloc(size, stack);

                for (var a : vkBufferCopies) {
                    SubCopyCommand subCopyCommand = subCopyCommands1.dequeue();
                    a.set(subCopyCommand.srcOffset(), subCopyCommand.dstOffset(), subCopyCommand.bufferSize());
                }

                GraphicsQueue.uploadBufferCmds(this.commandBuffers[currentFrame], stagingBufferId, bufferHandle, vkBufferCopies);
            }
            GraphicsQueue.UploadCmdWriteBarrier(this.commandBuffers[currentFrame].getHandle(), stack, this.hasBufferSwap);
            this.hasBufferSwap = false;
        }
        subCopyCommands.clear();
        GraphicsQueue.submitCommands(this.commandBuffers[currentFrame]);
    }

    public void uploadAsync(long bufferId, int dstOffset, int bufferSize, ByteBuffer src) {
        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
        stagingBuffer.copyBuffer(bufferSize, src);

        if (!subCopyCommands.containsKey(bufferId)) {
            subCopyCommands.put(bufferId, new ObjectArrayFIFOQueue<>(12));
        }
        subCopyCommands.get(bufferId).enqueue(new SubCopyCommand(stagingBuffer.getOffset(), dstOffset, bufferSize));

        this.recordedUploads[this.currentFrame].add(bufferId); // Use bufferId or appropriate object
    }

    public void updateFrame() {
        this.currentFrame = (this.currentFrame + 1) % FRAME_NUM;
        waitUploads(this.currentFrame);
    }

    public void waitUploads() {
        this.waitUploads(this.currentFrame);
    }

    private void waitUploads(int frame) {
        CommandPool.CommandBuffer commandBuffer = commandBuffers[frame];
        if (commandBuffer == null)
            return;
        Synchronization.waitFence(commandBuffers[frame].getFence());

        // Assuming all uploads are now ready, no specific method to mark them as ready

        this.commandBuffers[frame].reset();
        this.commandBuffers[frame] = null;
        this.recordedUploads[frame].clear();
    }

    public synchronized void waitAllUploads() {
        for (int i = 0; i < this.commandBuffers.length; ++i) {
            waitUploads(i);
        }
    }

    public void copyBuffer(long srcBuffer, long dstBuffer, int bufferSize) {
        if (commandBuffers[currentFrame] == null)
            this.commandBuffers[currentFrame] = GraphicsQueue.beginCommands();
        GraphicsQueue.uploadBufferCmd(this.commandBuffers[currentFrame], srcBuffer, 0, dstBuffer, 0, bufferSize);
    }
}
