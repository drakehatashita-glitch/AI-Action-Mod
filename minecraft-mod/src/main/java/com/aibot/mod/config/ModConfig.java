package com.aibot.mod.config;

import com.aibot.mod.AiMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("aimod.json");

    public static String ollamaUrl = "http://localhost:11434";
    public static String chatModel = "llama3.1:8b";
    public static String visionModel = "llava";
    public static double playerDetectionRange = 16.0;
    public static double attackRange = 4.0;
    public static int typingSpeedMsPerChar = 60;
    public static int typingSpeedVarianceMs = 30;
    public static boolean debugMode = false;

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                ConfigData data = GSON.fromJson(json, ConfigData.class);
                if (data != null) {
                    ollamaUrl = data.ollamaUrl != null ? data.ollamaUrl : ollamaUrl;
                    chatModel = data.chatModel != null ? data.chatModel : chatModel;
                    visionModel = data.visionModel != null ? data.visionModel : visionModel;
                    playerDetectionRange = data.playerDetectionRange > 0 ? data.playerDetectionRange : playerDetectionRange;
                    attackRange = data.attackRange > 0 ? data.attackRange : attackRange;
                    typingSpeedMsPerChar = data.typingSpeedMsPerChar > 0 ? data.typingSpeedMsPerChar : typingSpeedMsPerChar;
                    typingSpeedVarianceMs = data.typingSpeedVarianceMs >= 0 ? data.typingSpeedVarianceMs : typingSpeedVarianceMs;
                    debugMode = data.debugMode;
                }
            } catch (IOException e) {
                AiMod.LOGGER.error("Failed to load AI Mod config", e);
            }
        } else {
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            ConfigData data = new ConfigData();
            data.ollamaUrl = ollamaUrl;
            data.chatModel = chatModel;
            data.visionModel = visionModel;
            data.playerDetectionRange = playerDetectionRange;
            data.attackRange = attackRange;
            data.typingSpeedMsPerChar = typingSpeedMsPerChar;
            data.typingSpeedVarianceMs = typingSpeedVarianceMs;
            data.debugMode = debugMode;
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        } catch (IOException e) {
            AiMod.LOGGER.error("Failed to save AI Mod config", e);
        }
    }

    private static class ConfigData {
        String ollamaUrl = "http://localhost:11434";
        String chatModel = "llama3.1:8b";
        String visionModel = "llava";
        double playerDetectionRange = 16.0;
        double attackRange = 4.0;
        int typingSpeedMsPerChar = 60;
        int typingSpeedVarianceMs = 30;
        boolean debugMode = false;
    }
}
