package com.aibot.mod.features;

import com.aibot.mod.AiMod;
import com.aibot.mod.ai.LearnedResponseManager;
import com.aibot.mod.ai.OllamaClient;
import com.aibot.mod.ai.TypingSimulator;
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

    private static final String OLLAMA_SYSTEM = """
            You are a real Minecraft player. Keep responses short and casual like a real player would type them.
            Use common Minecraft slang. Never use emojis. Never be formal. Max 1-2 sentences.
            Examples: "lol", "yeah gg", "nah im busy", "nice one", "idk man", "fr fr", "nah that's cringe".
            """;

    private String lastOurResponse = null;
    private long lastResponseTime = 0;
    private String lastSuspiciousMessage = null;

    private static final long SUSPICIOUS_WINDOW_MS = 30_000;

    public ChatHandler(LearnedResponseManager learnedResponses, HumanMouseMovement mouseMovement) {
        this.learnedResponses = learnedResponses;
        this.mouseMovement = mouseMovement;
    }

    public void onChatMessage(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        String text = message.getString();
        String myName = client.player.getName().getString();

        boolean isDM = isDM(text, myName);
        boolean mentionsMe = text.toLowerCase().contains(myName.toLowerCase());

        if (!isDM && !mentionsMe) {
            if (lastOurResponse != null && isFollowUp(text)) {
                handleSuspiciousFollowUp(text);
            }
            return;
        }

        String senderMessage = extractMessageContent(text, myName);
        if (senderMessage == null || senderMessage.isBlank()) return;

        AiMod.LOGGER.info("Handling chat: {}", text);

        String commandAction = detectCommand(senderMessage);
        if (commandAction != null) {
            executeCommand(commandAction, client);
            return;
        }

        String known = learnedResponses.getKnownResponse(senderMessage);
        if (known != null) {
            TypingSimulator.sendWithDelay(known);
            lastOurResponse = known;
            lastResponseTime = System.currentTimeMillis();
            return;
        }

        String prompt = "A player in Minecraft said to you: \"" + senderMessage + "\". Reply naturally and casually. One or two words is fine.";
        OllamaClient.askWithContext(prompt, OLLAMA_SYSTEM).thenAccept(response -> {
            if (response != null && !response.isBlank()) {
                String clean = response.replaceAll("[\n\r]", " ").trim();
                if (clean.length() > 200) clean = clean.substring(0, 200);
                TypingSimulator.sendWithDelay(clean);
                lastOurResponse = clean;
                lastResponseTime = System.currentTimeMillis();
                learnedResponses.learn(senderMessage, clean);
            }
        });
    }

    private void handleSuspiciousFollowUp(String followUp) {
        long now = System.currentTimeMillis();
        if (now - lastResponseTime > SUSPICIOUS_WINDOW_MS) return;

        lastSuspiciousMessage = followUp;

        String prompt = "You are a Minecraft player. Your previous response was: \"" + lastOurResponse + "\"."
                + " The other player followed up with: \"" + followUp + "\"."
                + " They seem suspicious. What would you naturally say to calm them? Short and casual.";

        OllamaClient.askWithContext(prompt, OLLAMA_SYSTEM).thenAccept(improved -> {
            if (improved != null && !improved.isBlank()) {
                String clean = improved.replaceAll("[\n\r]", " ").trim();
                TypingSimulator.sendWithDelay(clean);
                learnedResponses.recordSuspiciousInteraction(lastOurResponse, followUp, clean);
                lastOurResponse = clean;
                lastResponseTime = System.currentTimeMillis();
                AiMod.LOGGER.info("Recovered from suspicious follow-up");
            }
        });
    }

    private boolean isFollowUp(String text) {
        String lower = text.toLowerCase();
        return lower.contains("bot") || lower.contains("macro") || lower.contains("cheat")
                || lower.contains("are you real") || lower.contains("human")
                || lower.contains("suspicious") || lower.contains("prove")
                || lower.contains("still there") || lower.contains("you there");
    }

    private String detectCommand(String message) {
        String lower = message.toLowerCase();
        if (lower.startsWith("say ")) return "say:" + message.substring(4).trim();
        if (lower.contains("spin")) return "spin";
        if (lower.contains("jump")) return "jump";
        if (lower.contains("crouch") || lower.contains("sneak")) return "crouch";
        if (lower.contains("look up")) return "lookup";
        if (lower.contains("look down")) return "lookdown";
        if (lower.startsWith("do ")) {
            return "do:" + message.substring(3).trim();
        }
        return null;
    }

    private void executeCommand(String command, MinecraftClient client) {
        if (client.player == null) return;
        ClientPlayerEntity player = client.player;

        if (command.startsWith("say:")) {
            String toSay = command.substring(4);
            TypingSimulator.sendWithDelay(toSay);
            return;
        }

        switch (command) {
            case "spin" -> {
                TypingSimulator.sendWithDelay("ok");
                new Thread(() -> {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    for (int i = 0; i < 40; i++) {
                        client.execute(() -> player.setYaw(player.getYaw() + 9f));
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    }
                }).start();
            }
            case "jump" -> {
                TypingSimulator.sendWithDelay("ok");
                client.execute(() -> client.options.jumpKey.setPressed(true));
                new Thread(() -> {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    client.execute(() -> client.options.jumpKey.setPressed(false));
                }).start();
            }
            case "crouch" -> {
                TypingSimulator.sendWithDelay("ok");
                client.execute(() -> player.setSneaking(true));
                new Thread(() -> {
                    try { Thread.sleep(1500 + random.nextInt(500)); } catch (InterruptedException ignored) {}
                    client.execute(() -> player.setSneaking(false));
                }).start();
            }
            case "lookup" -> {
                TypingSimulator.sendWithDelay("ok");
                client.execute(() -> player.setPitch(-70f));
            }
            case "lookdown" -> {
                TypingSimulator.sendWithDelay("ok");
                client.execute(() -> player.setPitch(70f));
            }
            default -> {
                String prompt = "A Minecraft player asked you to: \"" + command + "\". Reply casually that you'll do it or can't. One sentence max.";
                OllamaClient.ask(prompt).thenAccept(response -> {
                    if (response != null) {
                        TypingSimulator.sendWithDelay(response.trim());
                    }
                });
            }
        }
    }

    private boolean isDM(String text, String myName) {
        return text.startsWith(myName + " -> ") || text.startsWith("-> " + myName + ": ")
                || text.contains("whisper") || text.contains("msg") || text.contains("[PM]");
    }

    private String extractMessageContent(String text, String myName) {
        int colonIdx = text.indexOf(": ");
        if (colonIdx != -1) {
            String msg = text.substring(colonIdx + 2).trim();
            msg = msg.replace(myName, "").trim();
            return msg;
        }
        return text;
    }
}
