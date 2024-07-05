package net.vulkanmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.vulkanmod.config.video.VideoModeManager;
import net.vulkanmod.config.video.VideoModeSet;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class Config {

    public int frameQueueSize = 2;
    public boolean showDeviceRAM = true;
    public boolean pojavInfo = false;
    public VideoModeSet.VideoMode videoMode = VideoModeManager.getFirstAvailable().getVideoMode();
    public boolean windowedFullscreen = false;

    public int advCulling = 2;
    public boolean indirectDraw = false;

    public boolean perRenderTypeAreaBuffers = true;
    public boolean fastLeavesFix = false;
    public boolean entityCulling = true;
    public boolean animations = true;
    public boolean renderSky = true;
    public boolean renderSkyFog = true;
    public boolean renderCloudFog = true;
    public boolean postEffectFix = true;
    public boolean renderFog = true;
    public boolean entityOutline = true;
    public boolean dontUseImageSampled = false;
    public boolean resetHighUsageRec = false;
    public boolean showlowRAM = false;
    public boolean gaussianSkyBlending = false;
    public boolean depthWrite = false;
    public boolean forceFIFOVsync = false;
    public int presentMode = 1;
    public int ramInfoUpdate = 10;
    public int imageCount = 3;
    public int device = -1;

    public int ambientOcclusion = 1;

    public void write() {

        if(!Files.exists(CONFIG_PATH.getParent())) {
            try {
                Files.createDirectories(CONFIG_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            Files.write(CONFIG_PATH, Collections.singleton(GSON.toJson(this)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Path CONFIG_PATH;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    public static Config load(Path path) {
        Config config;
        Config.CONFIG_PATH = path;

        if (Files.exists(path)) {
            try (FileReader fileReader = new FileReader(path.toFile())) {
                config = GSON.fromJson(fileReader, Config.class);
            }
            catch (IOException exception) {
                throw new RuntimeException(exception.getMessage());
            }
        }
        else {
            config = null;
        }

        return config;
    }
}
