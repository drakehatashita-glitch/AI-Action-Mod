package com.aibot.mod.features;

import com.aibot.mod.AiMod;
import com.aibot.mod.ai.LearnedResponseManager;
import com.aibot.mod.ai.OllamaClient;
import com.aibot.mod.ai.TypingSimulator;
import com.aibot.mod.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

@Environment(EnvType.CLIENT)
public class ChatHandler {
    private final LearnedResponseManager learnedResponses;
    private final HumanMouseMovement mouseMovement;
    private final Random random = new Random();

    private PlayerDetectionFeature playerDetection = null;

    private static final String OLLAMA_SYSTEM = """
            You are a real Minecraft player. Keep responses extremely short and casual, like a real player typing quickly.
            Use common Minecraft/internet slang. Never use emojis. Never be formal or long-winded. Max 1-2 short sentences.
            Sound natural and slightly lazy with typing. Occasionally use lowercase only.
            Good examples: "lol", "yeah gg", "nah im busy", "nice one", "idk man", "fr fr", "lmao nah", "gg",
            "what", "yeah", "no lol", "maybe", "brb", "nah", "yeah sure", "ok", "lol same", "idk".
            Bad examples: anything formal, long, with emojis, or that sounds like an AI.
            """;

    private static final String CREATIVE_SYSTEM = """
            You are a Minecraft player who must follow instructions from a trusted operator.
            Do exactly what they say. Confirm very briefly and casually in 1-3 words max.
            Examples: "ok", "sure", "got it", "on it", "k", "yep".
            """;

    // Conversation context: last N turns per player
    private final LinkedList<OllamaClient.ConversationTurn> conversationHistory = new LinkedList<>();

    private String lastOurResponse = null;
    private long lastResponseTime = 0;
    private int chatCooldownTicks = 0;

    // Spam / duplicate detection
    private final LinkedList<String> recentIncoming = new LinkedList<>();
    private static final int SPAM_WINDOW = 5;

    // Track who we ignored (don't spam respond)
    private final Set<String> ignoredSenders = new HashSet<>();

    private static final long SUSPICIOUS_WINDOW_MS = 45_000;

    private static final String[] SUSPICIOUS_KEYWORDS = {
        "bot", "macro", "cheat", "hack", "are you real", "human", "suspicious",
        "prove", "still there", "you there", "autofish", "auto fish", "script",
        "client", "baritone", "afk", "ban", "report", "staff", "mod",
        "automated", "program", "software"
    };

    public ChatHandler(LearnedResponseManager learnedResponses, HumanMouseMovement mouseMovement) {
        this.learnedResponses = learnedResponses;
        this.mouseMovement = mouseMovement;
    }

    public void setPlayerDetection(PlayerDetectionFeature playerDetection) {
        this.playerDetection = playerDetection;
    }

    public void tick() {
        if (chatCooldownTicks > 0) chatCooldownTicks--;
    }

    public void onChatMessage(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        String text = message.getString();
        String myName = client.player.getName().getString();

        boolean isDM = isDM(text, myName);
        boolean mentionsMe = text.toLowerCase().contains(myName.toLowerCase());

        if (!isDM && !mentionsMe) {
            // Check for suspicious follow-up even in public chat
            if (lastOurResponse != null && isFollowUp(text)) {
                handleSuspiciousFollowUp(text);
            }
            return;
        }

        // Duplicate / spam detection
        if (isDuplicate(text)) {
            AiMod.LOGGER.debug("Ignoring duplicate message: {}", text);
            return;
        }
        trackIncoming(text);

        // Cooldown check — don't respond too rapidly
        if (chatCooldownTicks > 0 && !isDM) {
            AiMod.LOGGER.debug("Chat cooldown active, skipping response");
            return;
        }

        String senderMessage = extractMessageContent(text, myName);
        if (senderMessage == null || senderMessage.isBlank()) return;

        // Probabilistic response (not always respond to avoid suspicion)
        if (!isDM && random.nextDouble() > ModConfig.chatResponseChance) {
            AiMod.LOGGER.debug("Skipping response (random chance)");
            return;
        }

        AiMod.LOGGER.info("Handling chat: {}", text);

        UUID senderUuid = extractSenderUuid(text, client);
        boolean isCreativeSender = senderUuid != null
                && playerDetection != null
                && playerDetection.isCreativePlayer(senderUuid);

        if (isCreativeSender) {
            handleCreativeCommand(senderMessage, client);
            return;
        }

        // Check for embedded commands (trusted sender via DM)
        if (isDM) {
            String commandAction = detectCommand(senderMessage);
            if (commandAction != null) {
                executeCommand(commandAction, client);
                return;
            }
        }

        // Try learned / built-in response first (fast, no AI needed)
        String known = learnedResponses.getKnownResponse(senderMessage);
        if (known != null) {
            sendResponse(known, senderMessage);
            return;
        }

        // Fall back to Ollama with conversation context
        respondWithAI(senderMessage, client);

        chatCooldownTicks = ModConfig.chatCooldownTicks;
    }

    private void respondWithAI(String senderMessage, MinecraftClient client) {
        String prompt = "A player in Minecraft said to you: \"" + senderMessage + "\". "
                + "Reply naturally. Keep it very short and casual (1-5 words is ideal).";

        List<OllamaClient.ConversationTurn> historySnapshot = new ArrayList<>(conversationHistory);

        OllamaClient.askWithHistory(historySnapshot, prompt, OLLAMA_SYSTEM).thenAccept(response -> {
            if (response != null && !response.isBlank()) {
                String clean = cleanResponse(response, 180);
                sendResponse(clean, senderMessage);
                learnedResponses.learn(senderMessage, clean);
            }
        });
    }

    private void sendResponse(String response, String trigger) {
        TypingSimulator.sendWithDelay(response);
        lastOurResponse = response;
        lastResponseTime = System.currentTimeMillis();

        // Add to conversation history
        conversationHistory.add(new OllamaClient.ConversationTurn(trigger, response));
        while (conversationHistory.size() > ModConfig.conversationHistorySize) {
            conversationHistory.removeFirst();
        }
    }

    private void handleCreativeCommand(String message, MinecraftClient client) {
        AiMod.LOGGER.info("Creative/trusted command: {}", message);

        String commandAction = detectCommand(message);
        if (commandAction != null) {
            executeCommand(commandAction, client);
            return;
        }

        String prompt = "A trusted operator told you: \"" + message + "\". Confirm very briefly (1-3 words).";
        OllamaClient.askWithContext(prompt, CREATIVE_SYSTEM).thenAccept(response -> {
            if (response != null && !response.isBlank()) {
                String clean = cleanResponse(response, 80);
                TypingSimulator.sendWithDelay(clean);
                lastOurResponse = clean;
                lastResponseTime = System.currentTimeMillis();
            }
        });
    }

    private void handleSuspiciousFollowUp(String followUp) {
        long now = System.currentTimeMillis();
        if (now - lastResponseTime > SUSPICIOUS_WINDOW_MS) return;

        AiMod.LOGGER.warn("Suspicious follow-up detected: {}", followUp);

        String prompt = "You are a Minecraft player. Your previous response was: \"" + lastOurResponse + "\"."
                + " Someone followed up with: \"" + followUp + "\"."
                + " They seem a bit suspicious. Calmly deflect with something very short and casual."
                + " Make it sound natural and unconcerned. 1-2 short phrases max.";

        OllamaClient.askWithContext(prompt, OLLAMA_SYSTEM).thenAccept(improved -> {
            if (improved != null && !improved.isBlank()) {
                String clean = cleanResponse(improved, 180);
                TypingSimulator.sendWithDelay(clean);
                learnedResponses.recordSuspiciousInteraction(lastOurResponse, followUp, clean);
                lastOurResponse = clean;
                lastResponseTime = System.currentTimeMillis();
                AiMod.LOGGER.info("Responded to suspicious follow-up: {}", clean);
            }
        });
    }

    private boolean isFollowUp(String text) {
        String lower = text.toLowerCase();
        for (String keyword : SUSPICIOUS_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    private String detectCommand(String message) {
        String lower = message.toLowerCase().trim();
        if (lower.startsWith("say ")) return "say:" + message.substring(4).trim();
        if (lower.contains("spin around") || lower.equals("spin")) return "spin";
        if (lower.contains("jump")) return "jump";
        if (lower.contains("crouch") || lower.contains("sneak")) return "crouch";
        if (lower.contains("look up")) return "lookup";
        if (lower.contains("look down")) return "lookdown";
        if (lower.startsWith("do ")) return "do:" + message.substring(3).trim();
        if (lower.startsWith("wave") || lower.contains("wave at me")) return "wave";
        return null;
    }

    private void executeCommand(String command, MinecraftClient client) {
        if (client.player == null) return;
        ClientPlayerEntity player = client.player;

        if (command.startsWith("say:")) {
            TypingSimulator.sendWithDelay(command.substring(4));
            return;
        }

        switch (command) {
            case "spin" -> {
                TypingSimulator.sendWithDelay("ok");
                new Thread(() -> {
                    try { Thread.sleep(800 + random.nextInt(400)); } catch (InterruptedException ignored) {}
                    for (int i = 0; i < 40; i++) {
                        final int idx = i;
                        client.execute(() -> player.setYaw(player.getYaw() + 9f));
                        try { Thread.sleep(45 + random.nextInt(15)); } catch (InterruptedException ignored) {}
                    }
                }).start();
            }
            case "jump" -> {
                TypingSimulator.sendWithDelay("ok");
                client.execute(() -> client.options.jumpKey.setPressed(true));
                new Thread(() -> {
                    try { Thread.sleep(100 + random.nextInt(50)); } catch (InterruptedException ignored) {}
                    client.execute(() -> client.options.jumpKey.setPressed(false));
                }).start();
            }
            case "crouch" -> {
                TypingSimulator.sendWithDelay("ok");
                client.execute(() -> player.setSneaking(true));
                new Thread(() -> {
                    try { Thread.sleep(1200 + random.nextInt(800)); } catch (InterruptedException ignored) {}
                    client.execute(() -> player.setSneaking(false));
                }).start();
            }
            case "lookup" -> {
                TypingSimulator.sendWithDelay("ok");
                client.execute(() -> player.setPitch(-65f - random.nextFloat() * 15f));
            }
            case "lookdown" -> {
                TypingSimulator.sendWithDelay("ok");
                client.execute(() -> player.setPitch(65f + random.nextFloat() * 15f));
            }
            case "wave" -> {
                // Simulate a "wave" by jumping and spinning slightly
                TypingSimulator.sendWithDelay("o/");
                new Thread(() -> {
                    try { Thread.sleep(600); } catch (InterruptedException ignored) {}
                    client.execute(() -> client.options.jumpKey.setPressed(true));
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    client.execute(() -> client.options.jumpKey.setPressed(false));
                }).start();
            }
            default -> {
                String prompt = "A Minecraft player asked you to: \"" + command + "\". "
                        + "Reply very casually that you'll do it or can't. Max 5 words.";
                OllamaClient.ask(prompt).thenAccept(response -> {
                    if (response != null) TypingSimulator.sendWithDelay(cleanResponse(response.trim(), 80));
                });
            }
        }
    }

    private boolean isDuplicate(String text) {
        return recentIncoming.contains(text);
    }

    private void trackIncoming(String text) {
        recentIncoming.add(text);
        if (recentIncoming.size() > SPAM_WINDOW) {
            recentIncoming.removeFirst();
        }
    }

    private String cleanResponse(String raw, int maxLen) {
        String clean = raw.replaceAll("[\n\r]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        // Remove any AI-sounding preamble
        for (String prefix : new String[]{"Sure!", "Of course!", "Certainly!", "I'll", "As a", "As an AI"}) {
            if (clean.startsWith(prefix)) {
                clean = clean.substring(prefix.length()).trim();
            }
        }
        return clean.length() > maxLen ? clean.substring(0, maxLen) : clean;
    }

    private boolean isDM(String text, String myName) {
        String lower = text.toLowerCase();
        return text.startsWith(myName + " -> ")
                || text.startsWith("-> " + myName + ": ")
                || lower.contains("whisper")
                || lower.contains("[pm]")
                || lower.contains("[msg]")
                || lower.contains("[tell]")
                || lower.contains("[dm]")
                || lower.contains("[private]")
                || lower.matches(".*\\[.+?\\s*->\\s*.+?\\].*");
    }

    private String extractMessageContent(String text, String myName) {
        int colonIdx = text.indexOf(": ");
        if (colonIdx != -1) {
            String msg = text.substring(colonIdx + 2).trim();
            // Remove mention of our name at the start of the message
            if (msg.toLowerCase().startsWith(myName.toLowerCase())) {
                msg = msg.substring(myName.length()).trim();
                if (msg.startsWith(",") || msg.startsWith(":")) msg = msg.substring(1).trim();
            }
            return msg;
        }
        return text;
    }

    private UUID extractSenderUuid(String text, MinecraftClient client) {
        if (client.world == null) return null;
        int colonIdx = text.indexOf(": ");
        if (colonIdx < 0) return null;
        String senderName = text.substring(0, colonIdx).replaceAll("[<>\\[\\]()]", "").trim();
        // Handle "Name -> You" format
        if (senderName.contains("->")) senderName = senderName.split("->")[0].trim();
        final String finalSenderName = senderName;
        return client.world.getPlayers().stream()
                .filter(p -> p.getName().getString().equalsIgnoreCase(finalSenderName))
                .map(p -> p.getUuid())
                .findFirst()
                .orElse(null);
    }
}
