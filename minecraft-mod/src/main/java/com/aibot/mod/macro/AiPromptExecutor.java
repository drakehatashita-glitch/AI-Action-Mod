package com.aibot.mod.macro;

import com.aibot.mod.AiMod;
import com.aibot.mod.ai.OllamaClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Environment(EnvType.CLIENT)
public class AiPromptExecutor {
    private static final Gson GSON = new Gson();
    private static final Random RANDOM = new Random();

    private final MovementPlayback playback;
    private boolean executing = false;

    private static final String SYSTEM_PROMPT = """
            You are a Minecraft bot controller. The user gives you a plain English description of what they want to do in Minecraft.
            You must respond with ONLY a valid JSON object describing a sequence of actions.
            
            Available action types:
            - move_forward (ticks: int)
            - move_backward (ticks: int)
            - strafe_left (ticks: int)
            - strafe_right (ticks: int)
            - jump (ticks: int)
            - crouch (ticks: int)
            - sprint_forward (ticks: int)
            - attack (times: int, pause_between: int)
            - use (times: int, pause_between: int)
            - look (yaw_offset: float, pitch: float, ticks: int)
            - wait (ticks: int)
            
            1 second = 20 ticks. Respond ONLY with JSON like this:
            {
              "repeat": true,
              "actions": [
                {"type": "sprint_forward", "ticks": 40},
                {"type": "attack", "times": 3, "pause_between": 8},
                {"type": "wait", "ticks": 10}
              ]
            }
            
            No text before or after the JSON. Only the JSON object.
            """;

    public AiPromptExecutor(MovementPlayback playback) {
        this.playback = playback;
    }

    public void executePrompt(String userPrompt) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("[AI] Thinking about: \"" + userPrompt + "\"..."), true
            );
        }

        AiMod.LOGGER.info("AI Prompt: {}", userPrompt);

        String fullPrompt = "The Minecraft player wants to: " + userPrompt + "\nRespond with ONLY the JSON action sequence.";

        OllamaClient.askWithContext(fullPrompt, SYSTEM_PROMPT).thenAccept(response -> {
            if (response == null || response.isBlank()) {
                AiMod.LOGGER.error("Ollama returned no response for prompt");
                if (client.player != null) {
                    client.execute(() -> client.player.sendMessage(
                        Text.literal("[AI] Could not generate actions - is Ollama running?"), true
                    ));
                }
                return;
            }

            AiMod.LOGGER.info("AI response: {}", response);

            List<ActionFrame> frames = parseActionSequence(response);
            if (frames == null || frames.isEmpty()) {
                AiMod.LOGGER.error("Failed to parse AI action sequence: {}", response);
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.sendMessage(
                            Text.literal("[AI] Could not parse action sequence."), true
                        );
                    }
                });
                return;
            }

            boolean shouldRepeat = shouldRepeat(response);
            client.execute(() -> {
                playback.start(frames, shouldRepeat);
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal("[AI] Executing: \"" + userPrompt + "\" (" + frames.size() + " action steps, repeat=" + shouldRepeat + ")"), true
                    );
                }
            });
        });
    }

    private List<ActionFrame> parseActionSequence(String json) {
        try {
            String cleaned = extractJson(json);
            JsonObject root = JsonParser.parseString(cleaned).getAsJsonObject();
            var actionsArr = root.getAsJsonArray("actions");
            if (actionsArr == null) return null;

            List<ActionFrame> frames = new ArrayList<>();

            for (var elem : actionsArr) {
                JsonObject action = elem.getAsJsonObject();
                String type = action.get("type").getAsString().toLowerCase();
                frames.addAll(buildFrames(type, action));
            }

            return frames;
        } catch (Exception e) {
            AiMod.LOGGER.error("Failed to parse action JSON: {}", e.getMessage());
            return null;
        }
    }

    private List<ActionFrame> buildFrames(String type, JsonObject action) {
        List<ActionFrame> result = new ArrayList<>();
        int ticks = action.has("ticks") ? action.get("ticks").getAsInt() : 10;
        int times = action.has("times") ? action.get("times").getAsInt() : 1;
        int pauseBetween = action.has("pause_between") ? action.get("pause_between").getAsInt() : 5;

        switch (type) {
            case "move_forward" -> {
                ActionFrame f = new ActionFrame();
                f.forward = true;
                f.tickDuration = ticks;
                result.add(f);
            }
            case "move_backward" -> {
                ActionFrame f = new ActionFrame();
                f.backward = true;
                f.tickDuration = ticks;
                result.add(f);
            }
            case "strafe_left" -> {
                ActionFrame f = new ActionFrame();
                f.left = true;
                f.tickDuration = ticks;
                result.add(f);
            }
            case "strafe_right" -> {
                ActionFrame f = new ActionFrame();
                f.right = true;
                f.tickDuration = ticks;
                result.add(f);
            }
            case "sprint_forward" -> {
                ActionFrame f = new ActionFrame();
                f.forward = true;
                f.sprinting = true;
                f.tickDuration = ticks;
                result.add(f);
            }
            case "jump" -> {
                ActionFrame f = new ActionFrame();
                f.jumping = true;
                f.tickDuration = ticks;
                result.add(f);
            }
            case "crouch" -> {
                ActionFrame f = new ActionFrame();
                f.sneaking = true;
                f.tickDuration = ticks;
                result.add(f);
            }
            case "attack" -> {
                for (int i = 0; i < times; i++) {
                    ActionFrame attack = new ActionFrame();
                    attack.attacking = true;
                    attack.tickDuration = 2;
                    result.add(attack);

                    ActionFrame pause = new ActionFrame();
                    pause.tickDuration = pauseBetween;
                    result.add(pause);
                }
            }
            case "use" -> {
                for (int i = 0; i < times; i++) {
                    ActionFrame use = new ActionFrame();
                    use.using = true;
                    use.tickDuration = 2;
                    result.add(use);

                    ActionFrame pause = new ActionFrame();
                    pause.tickDuration = pauseBetween;
                    result.add(pause);
                }
            }
            case "wait" -> {
                ActionFrame f = new ActionFrame();
                f.tickDuration = ticks;
                result.add(f);
            }
            case "look" -> {
                float yawOff = action.has("yaw_offset") ? action.get("yaw_offset").getAsFloat() : 0f;
                float pitch = action.has("pitch") ? action.get("pitch").getAsFloat() : 0f;
                ActionFrame f = new ActionFrame();
                f.yaw = yawOff;
                f.pitch = pitch;
                f.tickDuration = ticks;
                result.add(f);
            }
        }

        return result;
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1) return text;
        return text.substring(start, end + 1);
    }

    private boolean shouldRepeat(String json) {
        try {
            String cleaned = extractJson(json);
            JsonObject root = JsonParser.parseString(cleaned).getAsJsonObject();
            return root.has("repeat") && root.get("repeat").getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }
}
