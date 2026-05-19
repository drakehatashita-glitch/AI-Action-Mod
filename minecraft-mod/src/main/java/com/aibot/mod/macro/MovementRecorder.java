package com.aibot.mod.macro;

import com.aibot.mod.AiMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class MovementRecorder {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path RECORDINGS_DIR = FabricLoader.getInstance().getGameDir().resolve("aimod/recordings");

    private boolean recording = false;
    private final List<ActionFrame> frames = new ArrayList<>();
    private String recordingName = "last";

    public void startRecording(String name) {
        frames.clear();
        recordingName = name;
        recording = true;
        AiMod.LOGGER.info("Recording started: {}", name);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[AI] Recording started — do your thing, press [R] to stop."), true);
        }
    }

    public void stopRecording() {
        if (!recording) return;
        recording = false;

        MinecraftClient client = MinecraftClient.getInstance();

        if (frames.isEmpty()) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("[AI] Recording stopped — no frames captured."), true);
            }
            return;
        }

        compressFrames();
        save(recordingName);
        AiMod.LOGGER.info("Recording stopped: {} frames saved as '{}'", frames.size(), recordingName);

        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("[AI] Saved " + frames.size() + " frames as '" + recordingName + "'. Press [P] to replay."), true
            );
        }
    }

    public void tick() {
        if (!recording) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        ClientPlayerEntity player = client.player;
        ActionFrame frame = new ActionFrame();

        frame.forward = client.options.forwardKey.isPressed();
        frame.backward = client.options.backKey.isPressed();
        frame.left = client.options.leftKey.isPressed();
        frame.right = client.options.rightKey.isPressed();
        frame.jumping = client.options.jumpKey.isPressed();
        frame.sneaking = client.options.sneakKey.isPressed();
        frame.sprinting = client.options.sprintKey.isPressed();
        frame.attacking = client.options.attackKey.isPressed();
        frame.using = client.options.useKey.isPressed();
        frame.yaw = player.getYaw();
        frame.pitch = player.getPitch();

        frames.add(frame);
    }

    private void compressFrames() {
        if (frames.size() < 2) return;
        List<ActionFrame> compressed = new ArrayList<>();
        ActionFrame current = frames.get(0);

        for (int i = 1; i < frames.size(); i++) {
            ActionFrame next = frames.get(i);
            if (framesEqual(current, next) && current.tickDuration < 20) {
                current.tickDuration++;
            } else {
                compressed.add(current);
                current = next.copy();
            }
        }
        compressed.add(current);
        frames.clear();
        frames.addAll(compressed);
    }

    private boolean framesEqual(ActionFrame a, ActionFrame b) {
        return a.forward == b.forward && a.backward == b.backward
                && a.left == b.left && a.right == b.right
                && a.jumping == b.jumping && a.sneaking == b.sneaking
                && a.sprinting == b.sprinting && a.attacking == b.attacking
                && a.using == b.using
                && Math.abs(a.yaw - b.yaw) < 1.0f
                && Math.abs(a.pitch - b.pitch) < 1.0f;
    }

    public void save(String name) {
        try {
            Files.createDirectories(RECORDINGS_DIR);
            Path file = RECORDINGS_DIR.resolve(name + ".json");
            Files.writeString(file, GSON.toJson(frames));
            AiMod.LOGGER.info("Saved recording to {}", file);
        } catch (IOException e) {
            AiMod.LOGGER.error("Failed to save recording", e);
        }
    }

    public List<ActionFrame> load(String name) {
        try {
            Path file = RECORDINGS_DIR.resolve(name + ".json");
            if (!Files.exists(file)) return null;
            String json = Files.readString(file);
            ActionFrame[] arr = GSON.fromJson(json, ActionFrame[].class);
            return arr != null ? List.of(arr) : null;
        } catch (IOException e) {
            AiMod.LOGGER.error("Failed to load recording: {}", name, e);
            return null;
        }
    }

    public List<ActionFrame> getLastFrames() {
        return new ArrayList<>(frames);
    }

    public boolean isRecording() { return recording; }

    public List<String> listRecordings() {
        try {
            Files.createDirectories(RECORDINGS_DIR);
            List<String> names = new ArrayList<>();
            Files.list(RECORDINGS_DIR)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> names.add(p.getFileName().toString().replace(".json", "")));
            return names;
        } catch (IOException e) {
            return List.of();
        }
    }
}
