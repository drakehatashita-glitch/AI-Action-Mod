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
    private static final Path RECORDINGS_DIR =
            FabricLoader.getInstance().getGameDir().resolve("aimod/recordings");

    private boolean recording = false;
    private final List<ActionFrame> frames = new ArrayList<>();
    private String recordingName = "last";

    private long recordingStartTime = 0;
    private int rawFrameCount = 0;

    // Track previous per-tick state to detect one-tick events
    private int lastHotbarSlot = -1;
    private boolean lastDropItem = false;
    private boolean lastSwapHands = false;
    private boolean lastPickBlock = false;

    // -------------------------------------------------------------------------
    // Start / stop
    // -------------------------------------------------------------------------

    public void startRecording(String name) {
        frames.clear();
        recordingName = name;
        recording = true;
        rawFrameCount = 0;
        recordingStartTime = System.currentTimeMillis();
        lastHotbarSlot = -1;
        lastDropItem = false;
        lastSwapHands = false;
        lastPickBlock = false;
        AiMod.LOGGER.info("Recording started: {}", name);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            lastHotbarSlot = getSelectedSlot(client);
            client.player.sendMessage(
                Text.literal("[AI] Recording started — press [R] to stop."), true);
        }
    }

    public void stopRecording() {
        if (!recording) return;
        recording = false;
        long durationMs = System.currentTimeMillis() - recordingStartTime;

        MinecraftClient client = MinecraftClient.getInstance();

        if (frames.isEmpty()) {
            if (client.player != null) {
                client.player.sendMessage(
                    Text.literal("[AI] Recording stopped — no frames captured."), true);
            }
            return;
        }

        int before = frames.size();
        compressFrames();
        int after = frames.size();
        float compressionRatio = before > 0 ? (float) after / before * 100f : 100f;

        save(recordingName);
        AiMod.LOGGER.info("Recording '{}': {}ms, {} raw frames -> {} compressed ({} %)",
                recordingName, durationMs, before, after,
                String.format("%.0f", compressionRatio));

        if (client.player != null) {
            client.player.sendMessage(Text.literal(String.format(
                "[AI] Saved '%s': %d frames (%.1fs, %.0f%% compressed).",
                recordingName, after, durationMs / 1000.0, compressionRatio
            )), true);
        }
    }

    // -------------------------------------------------------------------------
    // Per-tick recording (called from FeatureManager)
    // -------------------------------------------------------------------------

    public void tick() {
        if (!recording) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        ClientPlayerEntity player = client.player;
        ActionFrame frame = new ActionFrame();

        // --- Movement keys ---
        frame.forward   = client.options.forwardKey.isPressed();
        frame.backward  = client.options.backKey.isPressed();
        frame.left      = client.options.leftKey.isPressed();
        frame.right     = client.options.rightKey.isPressed();
        frame.jumping   = client.options.jumpKey.isPressed();
        frame.sneaking  = client.options.sneakKey.isPressed();
        frame.sprinting = client.options.sprintKey.isPressed();
        frame.attacking = client.options.attackKey.isPressed();
        frame.using     = client.options.useKey.isPressed();

        // --- Look direction ---
        frame.yaw   = player.getYaw();
        frame.pitch = player.getPitch();

        // --- Hotbar slot (record change only) ---
        int currentSlot = getSelectedSlot(client);
        if (currentSlot != lastHotbarSlot) {
            frame.hotbarSlot = currentSlot;
            lastHotbarSlot = currentSlot;
        }

        // --- Drop item key (Q) — rising-edge only ---
        boolean dropNow = client.options.dropKey.isPressed();
        frame.dropItem = dropNow && !lastDropItem;
        lastDropItem = dropNow;

        // --- Swap hands key (F) — rising-edge only ---
        boolean swapNow = client.options.swapHandsKey.isPressed();
        frame.swapHands = swapNow && !lastSwapHands;
        lastSwapHands = swapNow;

        // --- Pick block (middle click) — rising-edge only ---
        boolean pickNow = client.options.pickItemKey.isPressed();
        frame.pickBlock = pickNow && !lastPickBlock;
        lastPickBlock = pickNow;

        rawFrameCount++;
        frames.add(frame);
    }

    // -------------------------------------------------------------------------
    // Chat / command recording (called from ChatScreenMixin)
    // -------------------------------------------------------------------------

    /**
     * Records a chat message or command sent by the player.
     * Commands should be passed with their leading '/' included.
     * This inserts a dedicated event frame at the current recording position.
     */
    public void recordChatOrCommand(String text) {
        if (!recording || text == null || text.isBlank()) return;

        // Build a frame that carries only the chat event; movement state is neutral.
        ActionFrame frame = new ActionFrame();
        frame.chatMessage = text.trim();
        frames.add(frame);

        String kind = text.startsWith("/") ? "command" : "chat";
        AiMod.LOGGER.info("[Recorder] Captured {}: {}", kind, text);
    }

    // -------------------------------------------------------------------------
    // Compression
    // -------------------------------------------------------------------------

    private void compressFrames() {
        if (frames.size() < 2) return;

        List<ActionFrame> compressed = new ArrayList<>();
        ActionFrame current = frames.get(0).copy();

        for (int i = 1; i < frames.size(); i++) {
            ActionFrame next = frames.get(i);
            // Never merge frames that carry one-tick events
            if (!current.hasEvent() && !next.hasEvent()
                    && framesEqual(current, next)
                    && current.tickDuration < 40) {
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
        return a.forward    == b.forward
            && a.backward   == b.backward
            && a.left       == b.left
            && a.right      == b.right
            && a.jumping    == b.jumping
            && a.sneaking   == b.sneaking
            && a.sprinting  == b.sprinting
            && a.attacking  == b.attacking
            && a.using      == b.using
            && a.hotbarSlot == b.hotbarSlot
            && Math.abs(a.yaw   - b.yaw)   < 1.2f
            && Math.abs(a.pitch - b.pitch) < 1.2f;
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public void save(String name) {
        try {
            Files.createDirectories(RECORDINGS_DIR);
            Path file = RECORDINGS_DIR.resolve(sanitize(name) + ".json");
            Files.writeString(file, GSON.toJson(frames));
            AiMod.LOGGER.info("Saved recording '{}' to {}", name, file);
        } catch (IOException e) {
            AiMod.LOGGER.error("Failed to save recording '{}'", name, e);
        }
    }

    public void saveFrames(String name, List<ActionFrame> framesToSave) {
        try {
            Files.createDirectories(RECORDINGS_DIR);
            Path file = RECORDINGS_DIR.resolve(sanitize(name) + ".json");
            Files.writeString(file, GSON.toJson(framesToSave));
            AiMod.LOGGER.info("Saved {} frames as '{}'", framesToSave.size(), name);
        } catch (IOException e) {
            AiMod.LOGGER.error("Failed to save frames as '{}'", name, e);
        }
    }

    public boolean delete(String name) {
        try {
            Path file = RECORDINGS_DIR.resolve(sanitize(name) + ".json");
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) AiMod.LOGGER.info("Deleted recording '{}'", name);
            return deleted;
        } catch (IOException e) {
            AiMod.LOGGER.error("Failed to delete recording '{}'", name, e);
            return false;
        }
    }

    public List<ActionFrame> load(String name) {
        try {
            Path file = RECORDINGS_DIR.resolve(sanitize(name) + ".json");
            if (!Files.exists(file)) return null;
            String json = Files.readString(file);
            ActionFrame[] arr = GSON.fromJson(json, ActionFrame[].class);
            return arr != null ? new ArrayList<>(List.of(arr)) : null;
        } catch (IOException e) {
            AiMod.LOGGER.error("Failed to load recording '{}'", name, e);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public List<ActionFrame> getLastFrames() { return new ArrayList<>(frames); }
    public boolean isRecording()             { return recording; }
    public String  getRecordingName()        { return recordingName; }
    public int     getRawFrameCount()        { return rawFrameCount; }

    public List<String> listRecordings() {
        try {
            Files.createDirectories(RECORDINGS_DIR);
            List<String> names = new ArrayList<>();
            Files.list(RECORDINGS_DIR)
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .forEach(p -> names.add(
                        p.getFileName().toString().replace(".json", "")));
            return names;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public long getRecordingFileSizeBytes(String name) {
        try {
            Path file = RECORDINGS_DIR.resolve(sanitize(name) + ".json");
            return Files.exists(file) ? Files.size(file) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Returns the player's currently selected hotbar slot (0-8) by checking
     * which hotbar key is pressed, falling back to the last known slot.
     * Avoids direct access to PlayerInventory.selectedSlot which may be private
     * in some Yarn mapping versions.
     */
    private int getSelectedSlot(MinecraftClient client) {
        for (int i = 0; i < 9; i++) {
            if (client.options.hotbarKeys[i].isPressed()) return i;
        }
        // No hotbar key actively pressed — return cached value
        return lastHotbarSlot < 0 ? 0 : lastHotbarSlot;
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
