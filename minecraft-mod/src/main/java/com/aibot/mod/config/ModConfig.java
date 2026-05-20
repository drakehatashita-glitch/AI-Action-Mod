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

    // Ollama settings
    public static String ollamaUrl = "http://localhost:11434";
    public static String chatModel = "llama3.1:8b";
    public static String visionModel = "llava";
    public static int ollamaTimeoutSeconds = 30;
    public static double temperature = 0.85;
    public static int maxTokens = 120;

    // Detection ranges
    public static double playerDetectionRange = 20.0;
    public static double attackRange = 4.0;

    // Typing behaviour
    public static int typingSpeedMsPerChar = 55;
    public static int typingSpeedVarianceMs = 25;
    public static double typoChance = 0.04;

    // Chat behaviour
    public static double chatResponseChance = 0.90;
    public static int chatCooldownTicks = 40;
    public static int conversationHistorySize = 4;

    // Anti-AFK
    public static boolean antiAfkEnabled = true;
    public static int antiAfkIntervalTicks = 2400;

    // Auto-sell
    public static int sellInventoryThreshold = 28;
    public static String sellCommand = "/sell all";

    // Greeting
    public static double greetingChance = 0.80;

    // Debug
    public static boolean debugMode = false;

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                ConfigData data = GSON.fromJson(json, ConfigData.class);
                if (data != null) applyData(data);
            } catch (IOException e) {
                AiMod.LOGGER.error("Failed to load AI Mod config", e);
            }
        } else {
            save();
        }
    }

    private static void applyData(ConfigData d) {
        if (d.ollamaUrl != null) ollamaUrl = d.ollamaUrl;
        if (d.chatModel != null) chatModel = d.chatModel;
        if (d.visionModel != null) visionModel = d.visionModel;
        if (d.ollamaTimeoutSeconds > 0) ollamaTimeoutSeconds = d.ollamaTimeoutSeconds;
        if (d.temperature > 0) temperature = d.temperature;
        if (d.maxTokens > 0) maxTokens = d.maxTokens;

        if (d.playerDetectionRange > 0) playerDetectionRange = d.playerDetectionRange;
        if (d.attackRange > 0) attackRange = d.attackRange;

        if (d.typingSpeedMsPerChar > 0) typingSpeedMsPerChar = d.typingSpeedMsPerChar;
        if (d.typingSpeedVarianceMs >= 0) typingSpeedVarianceMs = d.typingSpeedVarianceMs;
        if (d.typoChance >= 0) typoChance = d.typoChance;

        if (d.chatResponseChance >= 0) chatResponseChance = d.chatResponseChance;
        if (d.chatCooldownTicks > 0) chatCooldownTicks = d.chatCooldownTicks;
        if (d.conversationHistorySize > 0) conversationHistorySize = d.conversationHistorySize;

        antiAfkEnabled = d.antiAfkEnabled;
        if (d.antiAfkIntervalTicks > 0) antiAfkIntervalTicks = d.antiAfkIntervalTicks;

        if (d.sellInventoryThreshold > 0) sellInventoryThreshold = d.sellInventoryThreshold;
        if (d.sellCommand != null) sellCommand = d.sellCommand;

        if (d.greetingChance >= 0) greetingChance = d.greetingChance;
        debugMode = d.debugMode;
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            ConfigData data = new ConfigData();
            data.ollamaUrl = ollamaUrl;
            data.chatModel = chatModel;
            data.visionModel = visionModel;
            data.ollamaTimeoutSeconds = ollamaTimeoutSeconds;
            data.temperature = temperature;
            data.maxTokens = maxTokens;
            data.playerDetectionRange = playerDetectionRange;
            data.attackRange = attackRange;
            data.typingSpeedMsPerChar = typingSpeedMsPerChar;
            data.typingSpeedVarianceMs = typingSpeedVarianceMs;
            data.typoChance = typoChance;
            data.chatResponseChance = chatResponseChance;
            data.chatCooldownTicks = chatCooldownTicks;
            data.conversationHistorySize = conversationHistorySize;
            data.antiAfkEnabled = antiAfkEnabled;
            data.antiAfkIntervalTicks = antiAfkIntervalTicks;
            data.sellInventoryThreshold = sellInventoryThreshold;
            data.sellCommand = sellCommand;
            data.greetingChance = greetingChance;
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
        int ollamaTimeoutSeconds = 30;
        double temperature = 0.85;
        int maxTokens = 120;
        double playerDetectionRange = 20.0;
        double attackRange = 4.0;
        int typingSpeedMsPerChar = 55;
        int typingSpeedVarianceMs = 25;
        double typoChance = 0.04;
        double chatResponseChance = 0.90;
        int chatCooldownTicks = 40;
        int conversationHistorySize = 4;
        boolean antiAfkEnabled = true;
        int antiAfkIntervalTicks = 2400;
        int sellInventoryThreshold = 28;
        String sellCommand = "/sell all";
        double greetingChance = 0.80;
        boolean debugMode = false;
    }
}
