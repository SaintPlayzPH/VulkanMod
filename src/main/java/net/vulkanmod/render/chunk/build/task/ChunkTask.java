package net.vulkanmod.render.chunk.build.task;

import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.build.RenderRegion;
import net.vulkanmod.render.chunk.build.TaskDispatcher;
import net.vulkanmod.render.chunk.build.thread.BuilderResources;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ChunkTask {
    public static final boolean BENCH = true;

    protected static TaskDispatcher taskDispatcher;
    protected final RenderSection section;
    public boolean highPriority = false;
    protected AtomicBoolean cancelled = new AtomicBoolean(false);

    ChunkTask(RenderSection renderSection) {
        this.section = renderSection;
    }

    public static BuildTask createBuildTask(RenderSection renderSection, RenderRegion renderRegion, boolean highPriority) {
        return new BuildTask(renderSection, renderRegion, highPriority);
    }

    public static void setTaskDispatcher(TaskDispatcher dispatcher) {
        taskDispatcher = dispatcher;
    }

    public abstract String name();

    public abstract Result runTask(BuilderResources builderResources);

    public void cancel() {
        this.cancelled.set(true);
    }

    public enum Result {
        CANCELLED,
        SUCCESSFUL
    }
}
