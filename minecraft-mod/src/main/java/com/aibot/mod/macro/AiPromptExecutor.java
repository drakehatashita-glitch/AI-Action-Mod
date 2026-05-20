package com.aibot.mod.macro;

import com.aibot.mod.AiMod;
import com.aibot.mod.ai.OllamaClient;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Environment(EnvType.CLIENT)
public class AiPromptExecutor {
    private static final Gson GSON = new Gson();
    private static final Random RANDOM = new Random();

    private final MovementPlayback playback;
    private MovementRecorder recorder = null;

    private static final String SYSTEM_PROMPT = """
            You are a Minecraft bot controller. Convert plain English instructions into a JSON action sequence.
            
            Available action types:
            - move_forward (ticks: int)
            - move_backward (ticks: int)
            - strafe_left (ticks: int)
            - strafe_right (ticks: int)
            - jump (ticks: int)
            - crouch (ticks: int)
            - sprint_forward (ticks: int)
            - sprint_jump (ticks: int) — sprint and jump simultaneously
            - attack (times: int, pause_between: int)
            - use (times: int, pause_between: int)
            - look (yaw_offset: float, pitch: float, ticks: int)
            - wait (ticks: int)
            - circle_strafe (radius_ticks: int, direction: "left"|"right")
            
            Rules:
            - 1 second = 20 ticks
            - "repeat": true means loop forever, false for one-time
            - "lock_mouse": true only if user explicitly says to keep mouse fixed
            - Respond ONLY with valid JSON, no other text
            
            Example:
            {
              "repeat": true,
              "lock_mouse": false,
              "description": "Sprint forward and attack repeatedly",
              "actions": [
                {"type": "sprint_forward", "ticks": 40},
                {"type": "attack", "times": 3, "pause_between": 8},
                {"type": "wait", "ticks": 10}
              ]
            }
            """;

    public AiPromptExecutor(MovementPlayback playback) {
        this.playback = playback;
    }

    public void setRecorder(MovementRecorder recorder) {
        this.recorder = recorder;
    }

    public void executePrompt(String userPrompt) {
        executeAndSave(userPrompt, null);
    }

    public void executeAndSave(String userPrompt, String saveName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("[AI] Thinking: \"" + truncate(userPrompt, 50) + "\"..."), true
            );
        }

        AiMod.LOGGER.info("AI Prompt: {}", userPrompt);

        boolean mouseLockHint = detectMouseLockHint(userPrompt);

        String fullPrompt = "Minecraft player instruction: " + userPrompt
                + "\nRespond with ONLY the JSON action sequence, no other text.";

        OllamaClient.askWithOptions(fullPrompt, SYSTEM_PROMPT, 0.3, 400).thenAccept(response -> {
            if (response == null || response.isBlank()) {
                AiMod.LOGGER.error("No response from Ollama for prompt");
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.sendMessage(
                            Text.literal("[AI] No response from Ollama — is it running?"), true);
                    }
                });
                return;
            }

            AiMod.LOGGER.info("AI response: {}", response);

            List<ActionFrame> frames = parseActionSequence(response);
            if (frames == null || frames.isEmpty()) {
                AiMod.LOGGER.error("Failed to parse AI action sequence: {}", response);
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("[AI] Could not parse action sequence — try rephrasing."), true);
                    }
                });
                return;
            }

            boolean shouldRepeat = shouldRepeat(response);
            boolean lockMouse = mouseLockHint || shouldLockMouse(response);

            client.execute(() -> {
                playback.setMouseLocked(lockMouse);
                playback.start(frames, shouldRepeat);

                if (saveName != null && !saveName.isBlank() && recorder != null) {
                    recorder.saveFrames(saveName, frames);
                    AiMod.LOGGER.info("Saved AI prompt recording as '{}'", saveName);
                }

                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal("[AI] Running: \"" + truncate(userPrompt, 40) + "\""
                            + (lockMouse ? " [mouse locked]" : "")
                            + (shouldRepeat ? " [looping]" : "")
                            + (saveName != null ? " — saved as '" + saveName + "'" : "")), true
                    );
                }
            });
        });
    }

    private boolean detectMouseLockHint(String prompt) {
        String lower = prompt.toLowerCase();
        return lower.contains("do not move my mouse")
            || lower.contains("don't move my mouse")
            || lower.contains("no mouse movement")
            || lower.contains("keep mouse")
            || lower.contains("mouse fixed")
            || lower.contains("same mouse")
            || lower.contains("lock mouse");
    }

    private List<ActionFrame> parseActionSequence(String json) {
        try {
            String cleaned = extractJson(json);
            JsonObject root = JsonParser.parseString(cleaned).getAsJsonObject();
            JsonArray actionsArr = root.getAsJsonArray("actions");
            if (actionsArr == null) return null;

            List<ActionFrame> frames = new ArrayList<>();
            for (var elem : actionsArr) {
                JsonObject action = elem.getAsJsonObject();
                String type = action.get("type").getAsString().toLowerCase();
                frames.addAll(buildFrames(type, action));
            }
            return frames.isEmpty() ? null : frames;
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

        // Clamp to sane values
        ticks = Math.max(1, Math.min(ticks, 2400));
        times = Math.max(1, Math.min(times, 50));
        pauseBetween = Math.max(1, Math.min(pauseBetween, 100));

        switch (type) {
            case "move_forward" -> {
                ActionFrame f = new ActionFrame();
                f.forward = true; f.tickDuration = ticks; result.add(f);
            }
            case "move_backward" -> {
                ActionFrame f = new ActionFrame();
                f.backward = true; f.tickDuration = ticks; result.add(f);
            }
            case "strafe_left" -> {
                ActionFrame f = new ActionFrame();
                f.left = true; f.tickDuration = ticks; result.add(f);
            }
            case "strafe_right" -> {
                ActionFrame f = new ActionFrame();
                f.right = true; f.tickDuration = ticks; result.add(f);
            }
            case "sprint_forward" -> {
                ActionFrame f = new ActionFrame();
                f.forward = true; f.sprinting = true; f.tickDuration = ticks; result.add(f);
            }
            case "sprint_jump" -> {
                ActionFrame f = new ActionFrame();
                f.forward = true; f.sprinting = true; f.jumping = true; f.tickDuration = ticks; result.add(f);
            }
            case "jump" -> {
                ActionFrame f = new ActionFrame();
                f.jumping = true; f.tickDuration = ticks; result.add(f);
            }
            case "crouch" -> {
                ActionFrame f = new ActionFrame();
                f.sneaking = true; f.tickDuration = ticks; result.add(f);
            }
            case "wait" -> {
                ActionFrame f = new ActionFrame();
                f.tickDuration = ticks; result.add(f);
            }
            case "attack" -> {
                for (int i = 0; i < times; i++) {
                    ActionFrame a = new ActionFrame();
                    a.attacking = true; a.tickDuration = 2; result.add(a);
                    ActionFrame p = new ActionFrame();
                    p.tickDuration = pauseBetween; result.add(p);
                }
            }
            case "use" -> {
                for (int i = 0; i < times; i++) {
                    ActionFrame u = new ActionFrame();
                    u.using = true; u.tickDuration = 2; result.add(u);
                    ActionFrame p = new ActionFrame();
                    p.tickDuration = pauseBetween; result.add(p);
                }
            }
            case "look" -> {
                ActionFrame f = new ActionFrame();
                f.yaw = action.has("yaw_offset") ? action.get("yaw_offset").getAsFloat() : 0f;
                f.pitch = action.has("pitch") ? action.get("pitch").getAsFloat() : 0f;
                f.tickDuration = ticks; result.add(f);
            }
            case "circle_strafe" -> {
                // Approximate circle strafe as alternating forward+strafe movements
                String dir = action.has("direction") ? action.get("direction").getAsString() : "left";
                int radiusTicks = action.has("radius_ticks") ? action.get("radius_ticks").getAsInt() : 40;
                boolean goLeft = "left".equalsIgnoreCase(dir);
                ActionFrame f1 = new ActionFrame();
                f1.forward = true; f1.sprinting = true; f1.left = goLeft; f1.right = !goLeft;
                f1.tickDuration = radiusTicks; result.add(f1);
            }
            default -> AiMod.LOGGER.warn("Unknown action type: {}", type);
        }
        return result;
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) return text;
        return text.substring(start, end + 1);
    }

    private boolean shouldRepeat(String json) {
        try {
            JsonObject root = JsonParser.parseString(extractJson(json)).getAsJsonObject();
            return root.has("repeat") && root.get("repeat").getAsBoolean();
        } catch (Exception e) { return false; }
    }

    private boolean shouldLockMouse(String json) {
        try {
            JsonObject root = JsonParser.parseString(extractJson(json)).getAsJsonObject();
            return root.has("lock_mouse") && root.get("lock_mouse").getAsBoolean();
        } catch (Exception e) { return false; }
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
