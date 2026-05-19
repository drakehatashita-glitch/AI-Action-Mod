package com.aibot.mod.ai;

import com.aibot.mod.AiMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class LearnedResponseManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_DIR = FabricLoader.getInstance().getGameDir().resolve("aimod");
    private static final Path RESPONSES_PATH = DATA_DIR.resolve("learned_responses.json");
    private static final Path SUSPICIOUS_PATH = DATA_DIR.resolve("suspicious_interactions.json");

    private final Map<String, LearnedEntry> learnedResponses = new LinkedHashMap<>();
    private final List<SuspiciousInteraction> suspiciousInteractions = new ArrayList<>();

    private static final Map<String, String[]> KNOWN_RESPONSES = new HashMap<>();

    static {
        KNOWN_RESPONSES.put("are you there", new String[]{
                "yeah", "yep", "yes", "here", "what's up", "yeah im here"
        });
        KNOWN_RESPONSES.put("are you afk", new String[]{
                "nah just chilling", "no im here", "not afk lol", "nope"
        });
        KNOWN_RESPONSES.put("are you a bot", new String[]{
                "no lol", "nope", "definitely not", "why would i be", "im literally here talking to you"
        });
        KNOWN_RESPONSES.put("you afk", new String[]{
                "nah just chilling", "no im here", "not afk lol"
        });
        KNOWN_RESPONSES.put("respond if human", new String[]{
                "im human lol", "yep im here", "human checking in"
        });
        KNOWN_RESPONSES.put("staff check", new String[]{
                "yeah im here", "present", "what's up", "yep"
        });
        KNOWN_RESPONSES.put("hello", new String[]{
                "hey", "hi", "sup", "hello", "hey whats up"
        });
        KNOWN_RESPONSES.put("hi", new String[]{
                "hey", "hi", "sup", "hello"
        });
        KNOWN_RESPONSES.put("hey", new String[]{
                "hey", "hi", "sup", "what's up"
        });
        KNOWN_RESPONSES.put("how are you", new String[]{
                "good you", "pretty good", "doing alright", "all good, you?"
        });
        KNOWN_RESPONSES.put("what are you doing", new String[]{
                "just grinding", "farming a bit", "chilling", "the usual"
        });
    }

    private static final Random RANDOM = new Random();

    public void load() {
        try {
            Files.createDirectories(DATA_DIR);
            if (Files.exists(RESPONSES_PATH)) {
                String json = Files.readString(RESPONSES_PATH);
                Type type = new TypeToken<Map<String, LearnedEntry>>() {}.getType();
                Map<String, LearnedEntry> loaded = GSON.fromJson(json, type);
                if (loaded != null) {
                    learnedResponses.putAll(loaded);
                }
                AiMod.LOGGER.info("Loaded {} learned responses", learnedResponses.size());
            }
        } catch (IOException e) {
            AiMod.LOGGER.error("Failed to load learned responses", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(DATA_DIR);
            Files.writeString(RESPONSES_PATH, GSON.toJson(learnedResponses));
            if (!suspiciousInteractions.isEmpty()) {
                Files.writeString(SUSPICIOUS_PATH, GSON.toJson(suspiciousInteractions));
            }
        } catch (IOException e) {
            AiMod.LOGGER.error("Failed to save learned responses", e);
        }
    }

    public String getKnownResponse(String message) {
        String lower = message.toLowerCase().trim();

        for (Map.Entry<String, String[]> entry : KNOWN_RESPONSES.entrySet()) {
            if (lower.contains(entry.getKey())) {
                String[] responses = entry.getValue();
                return responses[RANDOM.nextInt(responses.length)];
            }
        }

        String normalizedKey = normalizeKey(lower);
        if (learnedResponses.containsKey(normalizedKey)) {
            LearnedEntry entry = learnedResponses.get(normalizedKey);
            entry.useCount++;
            save();
            return entry.response;
        }

        return null;
    }

    public void learn(String trigger, String response) {
        String key = normalizeKey(trigger.toLowerCase().trim());
        LearnedEntry entry = new LearnedEntry();
        entry.trigger = trigger;
        entry.response = response;
        entry.learnedAt = System.currentTimeMillis();
        entry.useCount = 0;
        learnedResponses.put(key, entry);
        save();
        AiMod.LOGGER.info("Learned new response for: {}", trigger);
    }

    public void recordSuspiciousInteraction(String ourResponse, String theirFollowUp, String improvedResponse) {
        SuspiciousInteraction interaction = new SuspiciousInteraction();
        interaction.ourResponse = ourResponse;
        interaction.theirFollowUp = theirFollowUp;
        interaction.improvedResponse = improvedResponse;
        interaction.timestamp = System.currentTimeMillis();
        suspiciousInteractions.add(interaction);

        if (improvedResponse != null && !improvedResponse.isEmpty()) {
            String key = normalizeKey(theirFollowUp.toLowerCase().trim());
            learn(theirFollowUp, improvedResponse);
        }
        save();
    }

    private String normalizeKey(String text) {
        return text.replaceAll("[^a-z0-9 ]", "").trim();
    }

    public static class LearnedEntry {
        public String trigger;
        public String response;
        public long learnedAt;
        public int useCount;
    }

    public static class SuspiciousInteraction {
        public String ourResponse;
        public String theirFollowUp;
        public String improvedResponse;
        public long timestamp;
    }
}
