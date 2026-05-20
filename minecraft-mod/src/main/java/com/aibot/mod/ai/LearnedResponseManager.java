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
    private final Set<String> recentlyUsed = new LinkedHashSet<>();
    private static final int RECENT_USED_MAX = 6;

    private static final Map<String, String[]> KNOWN_RESPONSES = new LinkedHashMap<>();

    static {
        // Anti-bot checks
        KNOWN_RESPONSES.put("are you a bot", new String[]{
                "no lol", "nope", "definitely not lmao", "why would i be", "im literally talking to u",
                "haha no", "nah", "bot? me?? no", "lol no", "what makes u think that"
        });
        KNOWN_RESPONSES.put("are you afk", new String[]{
                "nah just chilling", "no im here", "not afk lol", "nope just grinding",
                "im here", "nah i was just looking away", "no why", "just busy lol"
        });
        KNOWN_RESPONSES.put("are you there", new String[]{
                "yeah", "yep", "yes", "here", "what's up", "yeah im here", "yep im here"
        });
        KNOWN_RESPONSES.put("you afk", new String[]{
                "nah just chilling", "no im here", "not afk lol", "nope", "im right here"
        });
        KNOWN_RESPONSES.put("respond if human", new String[]{
                "im human lol", "yep im here", "human checking in", "present lol", "here"
        });
        KNOWN_RESPONSES.put("staff check", new String[]{
                "yeah im here", "present", "what's up", "yep", "here"
        });
        KNOWN_RESPONSES.put("prove you're human", new String[]{
                "lol how", "what do u want me to do", "im literally here", "uh ok hi", "how do i prove that lol"
        });
        KNOWN_RESPONSES.put("are you real", new String[]{
                "yes?", "yeah lol", "im real", "yes im real", "why lol"
        });
        KNOWN_RESPONSES.put("you a bot", new String[]{
                "no", "nope lol", "haha no", "im a person", "lol why"
        });
        KNOWN_RESPONSES.put("macro", new String[]{
                "nah", "im just fast lol", "no macro", "lmao no", "i wish lol"
        });
        KNOWN_RESPONSES.put("cheating", new String[]{
                "nah", "clean", "no hacks lol", "im legit", "lol what"
        });
        KNOWN_RESPONSES.put("hacking", new String[]{
                "lol no", "clean client", "nope", "im legit bro", "no hacks"
        });

        // Greetings
        KNOWN_RESPONSES.put("hello", new String[]{
                "hey", "hi", "sup", "hello", "hey whats up", "heyyy", "oh hey"
        });
        KNOWN_RESPONSES.put("hi", new String[]{
                "hey", "hi", "sup", "hello", "heyyy", "oh hi"
        });
        KNOWN_RESPONSES.put("hey", new String[]{
                "hey", "hi", "sup", "what's up", "heyyy", "yo"
        });
        KNOWN_RESPONSES.put("sup", new String[]{
                "nm u", "nothing much", "just grinding", "chillin", "nm"
        });
        KNOWN_RESPONSES.put("yo", new String[]{
                "yo", "sup", "hey", "what's good", "ayo"
        });
        KNOWN_RESPONSES.put("wassup", new String[]{
                "nm u", "nothing much", "chillin", "just playing"
        });
        KNOWN_RESPONSES.put("howdy", new String[]{
                "lol hey", "hey", "howdy", "hi"
        });

        // How are you
        KNOWN_RESPONSES.put("how are you", new String[]{
                "good u", "pretty good", "doing alright", "all good u?", "good hbu", "solid u?"
        });
        KNOWN_RESPONSES.put("how r u", new String[]{
                "good u", "pretty good", "doing alright", "good hbu"
        });
        KNOWN_RESPONSES.put("hows it going", new String[]{
                "pretty good", "good u", "alright", "going well u?", "solid"
        });
        KNOWN_RESPONSES.put("what's up", new String[]{
                "nm just playing", "chillin", "grinding", "nothing much u", "just farming"
        });

        // What are you doing
        KNOWN_RESPONSES.put("what are you doing", new String[]{
                "just grinding", "farming a bit", "chilling", "the usual", "farming", "just playing"
        });
        KNOWN_RESPONSES.put("what r u doing", new String[]{
                "just grinding", "farming", "chilling", "playing lol"
        });
        KNOWN_RESPONSES.put("whatcha doing", new String[]{
                "just farming", "grinding", "nothing much", "playing"
        });

        // Compliments / Negative
        KNOWN_RESPONSES.put("nice", new String[]{
                "ty", "thanks", "lol thanks", "ik", "ty :)"
        });
        KNOWN_RESPONSES.put("good job", new String[]{
                "ty", "thanks lol", "appreciate it", "thx"
        });
        KNOWN_RESPONSES.put("noob", new String[]{
                "lol ok", "sure", "nah", "ur a noob", "ok bro"
        });
        KNOWN_RESPONSES.put("get rekt", new String[]{
                "lol", "sure", "ok", "lmao"
        });
        KNOWN_RESPONSES.put("ez", new String[]{
                "lol", "gg", "sure was", "ez fr", "gg ez"
        });
        KNOWN_RESPONSES.put("gg", new String[]{
                "gg", "gg wp", "ggs", "good game"
        });
        KNOWN_RESPONSES.put("lol", new String[]{
                "lol", "lmao", "haha", "ikr"
        });
        KNOWN_RESPONSES.put("lmao", new String[]{
                "lmao", "lol", "hahaha", "fr"
        });

        // Gameplay
        KNOWN_RESPONSES.put("trade", new String[]{
                "what u got", "what do u want", "maybe what r u selling", "for what"
        });
        KNOWN_RESPONSES.put("where are you", new String[]{
                "at spawn", "fishing spot", "farming", "around", "near spawn"
        });
        KNOWN_RESPONSES.put("join me", new String[]{
                "maybe later", "busy rn", "in a bit", "what for"
        });
        KNOWN_RESPONSES.put("wanna pvp", new String[]{
                "nah im good", "maybe later", "not rn", "i'm farming rn"
        });
        KNOWN_RESPONSES.put("want to party", new String[]{
                "sure", "yeah ok", "in a min", "maybe after this"
        });
        KNOWN_RESPONSES.put("help me", new String[]{
                "with what", "whats wrong", "what do u need", "i can try"
        });
        KNOWN_RESPONSES.put("can you help", new String[]{
                "with what", "maybe yeah", "what do u need", "sure whats up"
        });

        // Bye
        KNOWN_RESPONSES.put("bye", new String[]{
                "bye", "cya", "later", "peace", "bb"
        });
        KNOWN_RESPONSES.put("cya", new String[]{
                "cya", "later", "peace", "bye"
        });
        KNOWN_RESPONSES.put("gtg", new String[]{
                "ok cya", "later", "peace", "bye"
        });
        KNOWN_RESPONSES.put("brb", new String[]{
                "ok", "k", "np", "aight"
        });

        // Misc
        KNOWN_RESPONSES.put("thanks", new String[]{
                "np", "no problem", "sure", "anytime", "yep"
        });
        KNOWN_RESPONSES.put("ty", new String[]{
                "np", "no problem", "yep", "sure"
        });
        KNOWN_RESPONSES.put("ok", new String[]{
                "ok", "alright", "cool", "k"
        });
        KNOWN_RESPONSES.put("cool", new String[]{
                "lol yeah", "ikr", "yeah", "fr"
        });
        KNOWN_RESPONSES.put("wtf", new String[]{
                "lol", "what", "ikr", "yeah idk"
        });
        KNOWN_RESPONSES.put("omg", new String[]{
                "lol", "ikr", "what happened", "fr?"
        });
        KNOWN_RESPONSES.put("fr", new String[]{
                "fr", "ikr", "literally", "yeah for real"
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

        // Exact and substring matching against built-in responses
        for (Map.Entry<String, String[]> entry : KNOWN_RESPONSES.entrySet()) {
            if (lower.contains(entry.getKey())) {
                String[] responses = entry.getValue();
                String chosen = pickNonRepeating(entry.getKey(), responses);
                trackRecentlyUsed(chosen);
                return chosen;
            }
        }

        // Fuzzy match against learned responses
        String normalizedKey = normalizeKey(lower);
        String bestMatch = findBestLearnedMatch(normalizedKey);
        if (bestMatch != null) {
            LearnedEntry entry = learnedResponses.get(bestMatch);
            entry.useCount++;
            save();
            return entry.response;
        }

        return null;
    }

    private String pickNonRepeating(String key, String[] responses) {
        if (responses.length == 1) return responses[0];

        // Try up to 3 times to pick one that wasn't recently used
        for (int i = 0; i < 3; i++) {
            String candidate = responses[RANDOM.nextInt(responses.length)];
            if (!recentlyUsed.contains(candidate)) {
                return candidate;
            }
        }
        return responses[RANDOM.nextInt(responses.length)];
    }

    private void trackRecentlyUsed(String response) {
        recentlyUsed.add(response);
        if (recentlyUsed.size() > RECENT_USED_MAX) {
            recentlyUsed.remove(recentlyUsed.iterator().next());
        }
    }

    private String findBestLearnedMatch(String key) {
        // Exact match first
        if (learnedResponses.containsKey(key)) return key;

        // Substring match
        for (String k : learnedResponses.keySet()) {
            if (key.contains(k) || k.contains(key)) {
                return k;
            }
        }

        // Edit distance match (for short keys)
        if (key.length() <= 20) {
            String closest = null;
            int closestDist = Integer.MAX_VALUE;
            for (String k : learnedResponses.keySet()) {
                if (Math.abs(k.length() - key.length()) > 4) continue;
                int dist = editDistance(key, k);
                if (dist < closestDist && dist <= 2) {
                    closestDist = dist;
                    closest = k;
                }
            }
            return closest;
        }

        return null;
    }

    private int editDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) dp[i][j] = dp[i - 1][j - 1];
                else dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
            }
        }
        return dp[a.length()][b.length()];
    }

    public void learn(String trigger, String response) {
        String key = normalizeKey(trigger.toLowerCase().trim());
        if (key.isEmpty()) return;
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
