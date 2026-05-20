package com.aibot.mod.macro;

import com.aibot.mod.AiMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;

@Environment(EnvType.CLIENT)
public class MovementPlayback {
    private boolean playing = false;
    private boolean paused = false;
    private List<ActionFrame> frames;
    private int currentFrameIndex = 0;
    private int currentFrameTick = 0;
    private boolean looping = true;
    private int loopCount = 0;

    // Mouse variation flags
    private boolean mouseLocked = false;

    // Per-loop variation (recalculated each loop)
    private float yawVariation = 0f;
    private float pitchVariation = 0f;
    private float timingDriftFactor = 1.0f;

    // Per-tick micro-noise
    private float yawNoise = 0f;
    private float pitchNoise = 0f;
    private int noiseRefreshTimer = 0;

    private int extraPauseTicks = 0;
    private int variationRefreshTimer = 0;

    // Teleport detection
    private Vec3d lastPosition = null;
    private static final double TELEPORT_THRESHOLD_SQ = 64.0; // 8 blocks squared
    private boolean scanning = false;
    private float scanYaw = 0f;
    private int scanDirection = 1;
    private int scanTimer = 0;

    private static final Random RANDOM = new Random();

    public void start(List<ActionFrame> frames, boolean loop) {
        this.frames = frames;
        this.looping = loop;
        this.currentFrameIndex = 0;
        this.currentFrameTick = 0;
        this.loopCount = 0;
        this.playing = true;
        this.paused = false;
        this.scanning = false;
        this.lastPosition = null;
        refreshVariation();
        AiMod.LOGGER.info("Playback started: {} frames, loop={}", frames.size(), loop);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[AI] Playback started. Press [P] to stop."), true);
        }
    }

    public void stop() {
        if (!playing) return;
        playing = false;
        paused = false;
        scanning = false;
        releaseAllKeys();
        AiMod.LOGGER.info("Playback stopped after {} loops.", loopCount);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[AI] Playback stopped."), true);
        }
    }

    public void pause() {
        if (!playing) return;
        paused = true;
        releaseAllKeys();
    }

    public void resume() {
        paused = false;
    }

    public void setMouseLocked(boolean locked) {
        this.mouseLocked = locked;
    }

    public void tick() {
        if (!playing || frames == null || frames.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // --- Teleport detection ---
        Vec3d pos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        if (lastPosition != null && !paused && !scanning) {
            double distSq = pos.squaredDistanceTo(lastPosition);
            if (distSq > TELEPORT_THRESHOLD_SQ) {
                AiMod.LOGGER.warn("Teleport detected! Stopping playback and entering scan mode.");
                releaseAllKeys();
                scanning = true;
                scanYaw = client.player.getYaw();
                scanTimer = 0;
                client.player.sendMessage(
                    Text.literal("[AI] Teleported! Scanning... Press [P] to resume playback."), true);
            }
        }
        lastPosition = pos;

        // --- Scan mode ---
        if (scanning) {
            tickScan(client);
            return;
        }

        if (paused) {
            return;
        }

        if (extraPauseTicks > 0) {
            extraPauseTicks--;
            releaseAllKeys();
            return;
        }

        variationRefreshTimer--;
        if (variationRefreshTimer <= 0) {
            refreshVariation();
        }

        noiseRefreshTimer--;
        if (noiseRefreshTimer <= 0) {
            if (!mouseLocked) {
                yawNoise = (RANDOM.nextFloat() - 0.5f) * 0.8f;
                pitchNoise = (RANDOM.nextFloat() - 0.5f) * 0.4f;
            } else {
                yawNoise = 0f;
                pitchNoise = 0f;
            }
            noiseRefreshTimer = 3 + RANDOM.nextInt(5);
        }

        ActionFrame frame = frames.get(currentFrameIndex);
        applyFrame(client, frame);

        currentFrameTick++;
        int effectiveDuration = mouseLocked
            ? frame.tickDuration
            : Math.max(1, Math.round(frame.tickDuration * timingDriftFactor));

        if (currentFrameTick >= effectiveDuration) {
            currentFrameTick = 0;
            currentFrameIndex++;

            if (!mouseLocked && RANDOM.nextInt(100) < 3) {
                extraPauseTicks = RANDOM.nextInt(3) + 1;
            }

            if (currentFrameIndex >= frames.size()) {
                loopCount++;
                if (looping) {
                    currentFrameIndex = 0;
                    refreshVariation();
                    extraPauseTicks = 5 + RANDOM.nextInt(20);
                } else {
                    stop();
                }
            }
        }
    }

    private void tickScan(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        scanTimer++;

        // Sweep yaw back and forth slowly, bob pitch slightly
        scanYaw += scanDirection * 0.8f;
        float pitch = (float)(Math.sin(scanTimer * 0.05) * 15.0);

        if (Math.abs(scanYaw - player.getYaw()) > 60f) {
            scanDirection *= -1;
        }

        player.setYaw(scanYaw);
        player.setPitch(pitch);
    }

    public void stopScan() {
        scanning = false;
    }

    private void applyFrame(MinecraftClient client, ActionFrame frame) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        client.options.forwardKey.setPressed(frame.forward);
        client.options.backKey.setPressed(frame.backward);
        client.options.leftKey.setPressed(frame.left);
        client.options.rightKey.setPressed(frame.right);
        client.options.jumpKey.setPressed(frame.jumping);
        client.options.sneakKey.setPressed(frame.sneaking);
        client.options.sprintKey.setPressed(frame.sprinting || (frame.forward && !frame.sneaking && RANDOM.nextFloat() < 0.95f));
        client.options.attackKey.setPressed(frame.attacking);
        client.options.useKey.setPressed(frame.using);

        if (mouseLocked) {
            player.setYaw(frame.yaw);
            player.setPitch(frame.pitch);
        } else {
            float newYaw = frame.yaw + yawVariation + yawNoise;
            float newPitch = Math.max(-90f, Math.min(90f, frame.pitch + pitchVariation + pitchNoise));
            player.setYaw(newYaw);
            player.setPitch(newPitch);
        }
    }

    private void refreshVariation() {
        if (!mouseLocked) {
            yawVariation = (RANDOM.nextFloat() - 0.5f) * 10f;
            pitchVariation = (RANDOM.nextFloat() - 0.5f) * 5f;
            timingDriftFactor = 0.90f + RANDOM.nextFloat() * 0.20f;
        } else {
            yawVariation = 0f;
            pitchVariation = 0f;
            timingDriftFactor = 1.0f;
        }
        variationRefreshTimer = 60 + RANDOM.nextInt(80);
    }

    private void releaseAllKeys() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.sneakKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.attackKey.setPressed(false);
        client.options.useKey.setPressed(false);
    }

    public boolean isPlaying() { return playing; }
    public boolean isPaused() { return paused; }
    public boolean isScanning() { return scanning; }
    public boolean isMouseLocked() { return mouseLocked; }
    public int getLoopCount() { return loopCount; }
    public int getCurrentFrame() { return currentFrameIndex; }
    public int getTotalFrames() { return frames != null ? frames.size() : 0; }
}
