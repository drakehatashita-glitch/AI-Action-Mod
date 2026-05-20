package com.aibot.mod.macro;

import com.aibot.mod.AiMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

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

    private static final Random RANDOM = new Random();

    // Per-loop variation offsets — recalculated each loop
    private float yawVariation = 0f;
    private float pitchVariation = 0f;

    // Per-frame micro-noise — tiny random jitter each tick
    private float yawNoise = 0f;
    private float pitchNoise = 0f;
    private int noiseRefreshTimer = 0;

    // Timing drift — each loop the frame durations shift slightly
    private float timingDriftFactor = 1.0f;

    private int extraPauseTicks = 0;
    private int variationRefreshTimer = 0;

    public void start(List<ActionFrame> frames, boolean loop) {
        this.frames = frames;
        this.looping = loop;
        this.currentFrameIndex = 0;
        this.currentFrameTick = 0;
        this.loopCount = 0;
        this.playing = true;
        this.paused = false;
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

    public void tick() {
        if (!playing || paused || frames == null || frames.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (extraPauseTicks > 0) {
            extraPauseTicks--;
            releaseAllKeys();
            return;
        }

        // Refresh per-loop variation timer
        variationRefreshTimer--;
        if (variationRefreshTimer <= 0) {
            refreshVariation();
        }

        // Refresh micro-noise more frequently
        noiseRefreshTimer--;
        if (noiseRefreshTimer <= 0) {
            yawNoise = (RANDOM.nextFloat() - 0.5f) * 0.8f;
            pitchNoise = (RANDOM.nextFloat() - 0.5f) * 0.4f;
            noiseRefreshTimer = 3 + RANDOM.nextInt(5);
        }

        ActionFrame frame = frames.get(currentFrameIndex);
        applyFrame(client, frame);

        currentFrameTick++;

        // Timing drift: each frame lasts slightly longer or shorter than recorded
        int effectiveDuration = Math.max(1, Math.round(frame.tickDuration * timingDriftFactor));

        if (currentFrameTick >= effectiveDuration) {
            currentFrameTick = 0;
            currentFrameIndex++;

            // Small random micro-pause between frames (~3% chance)
            if (RANDOM.nextInt(100) < 3) {
                extraPauseTicks = RANDOM.nextInt(3) + 1;
            }

            if (currentFrameIndex >= frames.size()) {
                loopCount++;
                if (looping) {
                    currentFrameIndex = 0;
                    refreshVariation();
                    // Variable pause between loops (0.25 – 1.25 seconds)
                    extraPauseTicks = 5 + RANDOM.nextInt(20);
                } else {
                    stop();
                }
            }
        }
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

        // Combine: per-loop yaw/pitch offset + per-tick micro-noise
        float newYaw = frame.yaw + yawVariation + yawNoise;
        float newPitch = frame.pitch + pitchVariation + pitchNoise;
        newPitch = Math.max(-90f, Math.min(90f, newPitch));

        player.setYaw(newYaw);
        player.setPitch(newPitch);
    }

    private void refreshVariation() {
        // Each loop: slightly different look direction (±5° yaw, ±2.5° pitch)
        yawVariation = (RANDOM.nextFloat() - 0.5f) * 10f;
        pitchVariation = (RANDOM.nextFloat() - 0.5f) * 5f;

        // Timing drift: 90%–110% of recorded speed
        timingDriftFactor = 0.90f + RANDOM.nextFloat() * 0.20f;

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
    public int getLoopCount() { return loopCount; }
    public int getCurrentFrame() { return currentFrameIndex; }
    public int getTotalFrames() { return frames != null ? frames.size() : 0; }
}
