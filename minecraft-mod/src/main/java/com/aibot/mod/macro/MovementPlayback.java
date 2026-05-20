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
    private boolean paused  = false;
    private List<ActionFrame> frames;
    private int currentFrameIndex = 0;
    private int currentFrameTick  = 0;
    private boolean looping   = true;
    private int loopCount     = 0;

    private boolean mouseLocked = false;

    // Per-loop variation
    private float yawVariation   = 0f;
    private float pitchVariation = 0f;
    private float timingDriftFactor = 1.0f;

    // Per-tick micro-noise
    private float yawNoise    = 0f;
    private float pitchNoise  = 0f;
    private int noiseRefreshTimer    = 0;
    private int variationRefreshTimer = 0;

    private int extraPauseTicks = 0;

    // Anti-pattern: human break between loops
    private int loopsSinceBreak = 0;
    private static final int MAX_LOOPS_BEFORE_BREAK = 12;
    private boolean takingBreak = false;
    private int breakDurationTicks = 0;

    // Teleport detection
    private Vec3d lastPosition = null;
    private static final double TELEPORT_THRESHOLD_SQ = 64.0;
    private boolean scanning = false;
    private float scanYaw = 0f;
    private int scanDirection = 1;
    private int scanTimer = 0;

    private static final Random RANDOM = new Random();

    // -------------------------------------------------------------------------
    // Control
    // -------------------------------------------------------------------------

    public void start(List<ActionFrame> frames, boolean loop) {
        this.frames = frames;
        this.looping = loop;
        this.currentFrameIndex = 0;
        this.currentFrameTick  = 0;
        this.loopCount         = 0;
        this.loopsSinceBreak   = 0;
        this.playing           = true;
        this.paused            = false;
        this.scanning          = false;
        this.takingBreak       = false;
        this.lastPosition      = null;
        refreshVariation();
        AiMod.LOGGER.info("Playback started: {} frames, loop={}", frames.size(), loop);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("[AI] Playback started. Press [P] to stop."), true);
        }
    }

    public void stop() {
        if (!playing) return;
        playing  = false;
        paused   = false;
        scanning = false;
        takingBreak = false;
        releaseAllKeys();
        AiMod.LOGGER.info("Playback stopped after {} loops.", loopCount);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[AI] Playback stopped."), true);
        }
    }

    public void pause()  { if (playing) { paused = true;  releaseAllKeys(); } }
    public void resume() { paused = false; }

    public void setMouseLocked(boolean locked) { this.mouseLocked = locked; }

    // -------------------------------------------------------------------------
    // Main tick
    // -------------------------------------------------------------------------

    public void tick() {
        if (!playing || frames == null || frames.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Teleport detection
        Vec3d pos = new Vec3d(
            client.player.getX(), client.player.getY(), client.player.getZ());
        if (lastPosition != null && !paused && !scanning) {
            double distSq = pos.squaredDistanceTo(lastPosition);
            if (distSq > TELEPORT_THRESHOLD_SQ) {
                AiMod.LOGGER.warn("Teleport detected! Entering scan mode.");
                releaseAllKeys();
                scanning = true;
                scanYaw  = client.player.getYaw();
                scanTimer = 0;
                client.player.sendMessage(
                    Text.literal("[AI] Teleported — scanning environment."), true);
            }
        }
        lastPosition = pos;

        if (scanning) { tickScan(client); return; }
        if (paused)   return;

        // Human break between loops
        if (takingBreak) {
            breakDurationTicks--;
            releaseAllKeys();
            if (breakDurationTicks <= 0) {
                takingBreak = false;
                loopsSinceBreak = 0;
                AiMod.LOGGER.info("Break over, resuming playback");
            }
            return;
        }

        if (extraPauseTicks > 0) {
            extraPauseTicks--;
            releaseAllKeys();
            return;
        }

        variationRefreshTimer--;
        if (variationRefreshTimer <= 0) refreshVariation();

        noiseRefreshTimer--;
        if (noiseRefreshTimer <= 0) {
            if (!mouseLocked) {
                yawNoise   = (float)(RANDOM.nextGaussian() * 0.35f);
                pitchNoise = (float)(RANDOM.nextGaussian() * 0.18f);
            } else {
                yawNoise = pitchNoise = 0f;
            }
            noiseRefreshTimer = 3 + RANDOM.nextInt(6);
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

            // Occasional micro-pause mid-sequence
            if (!mouseLocked && RANDOM.nextInt(120) < 3) {
                extraPauseTicks = RANDOM.nextInt(4) + 1;
            }

            if (currentFrameIndex >= frames.size()) {
                loopCount++;
                loopsSinceBreak++;

                if (looping) {
                    currentFrameIndex = 0;
                    refreshVariation();
                    extraPauseTicks = 5 + RANDOM.nextInt(15);

                    if (loopsSinceBreak >= MAX_LOOPS_BEFORE_BREAK
                            && RANDOM.nextFloat() < 0.5f) {
                        startHumanBreak(client);
                    }
                } else {
                    stop();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Apply a single frame to the game
    // -------------------------------------------------------------------------

    private void applyFrame(MinecraftClient client, ActionFrame frame) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // --- Movement keys ---
        client.options.forwardKey.setPressed(frame.forward);
        client.options.backKey.setPressed(frame.backward);
        client.options.leftKey.setPressed(frame.left);
        client.options.rightKey.setPressed(frame.right);
        client.options.jumpKey.setPressed(frame.jumping);
        client.options.sneakKey.setPressed(frame.sneaking);

        boolean shouldSprint = frame.sprinting
                || (frame.forward && !frame.sneaking && RANDOM.nextFloat() < 0.93f);
        client.options.sprintKey.setPressed(shouldSprint);

        client.options.attackKey.setPressed(frame.attacking);
        client.options.useKey.setPressed(frame.using);

        // --- Look direction ---
        if (mouseLocked) {
            player.setYaw(frame.yaw);
            player.setPitch(frame.pitch);
        } else {
            float newYaw   = frame.yaw + yawVariation + yawNoise;
            float newPitch = Math.max(-90f,
                Math.min(90f, frame.pitch + pitchVariation + pitchNoise));
            player.setYaw(newYaw);
            player.setPitch(newPitch);
        }

        // --- Hotbar slot change (simulate key press rather than direct field write) ---
        if (frame.hotbarSlot >= 0 && frame.hotbarSlot <= 8) {
            final int slot = frame.hotbarSlot;
            client.options.hotbarKeys[slot].setPressed(true);
            client.execute(() -> client.options.hotbarKeys[slot].setPressed(false));
        }

        // --- One-tick event actions ---
        if (frame.dropItem) {
            // Press and immediately release Q (drops one item from hand)
            client.options.dropKey.setPressed(true);
            client.execute(() -> client.options.dropKey.setPressed(false));
        }

        if (frame.swapHands) {
            client.options.swapHandsKey.setPressed(true);
            client.execute(() -> client.options.swapHandsKey.setPressed(false));
        }

        if (frame.pickBlock) {
            client.options.pickItemKey.setPressed(true);
            client.execute(() -> client.options.pickItemKey.setPressed(false));
        }

        // --- Chat message or command ---
        if (frame.chatMessage != null && !frame.chatMessage.isBlank()) {
            final String msg = frame.chatMessage;
            client.execute(() -> {
                if (client.getNetworkHandler() == null) return;
                if (msg.startsWith("/")) {
                    // Strip leading slash — sendChatCommand expects bare command
                    client.getNetworkHandler().sendChatCommand(msg.substring(1));
                } else {
                    client.getNetworkHandler().sendChatMessage(msg);
                }
                AiMod.LOGGER.info("[Playback] Sent {}: {}",
                    msg.startsWith("/") ? "command" : "chat", msg);
            });
        }
    }

    // -------------------------------------------------------------------------
    // Scan behaviour (post-teleport)
    // -------------------------------------------------------------------------

    private void tickScan(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        scanTimer++;
        scanYaw += scanDirection * 0.6f + (RANDOM.nextFloat() - 0.5f) * 0.2f;
        float pitch = (float)(Math.sin(scanTimer * 0.04) * 18.0);

        if (Math.abs(scanYaw - player.getYaw()) > 55f) scanDirection *= -1;

        player.setYaw(scanYaw);
        player.setPitch(pitch);
    }

    public void stopScan() { scanning = false; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void startHumanBreak(MinecraftClient client) {
        takingBreak = true;
        breakDurationTicks = 60 + RANDOM.nextInt(180);
        AiMod.LOGGER.info("Taking human-like break for {} ticks", breakDurationTicks);

        if (client.player != null && com.aibot.mod.config.ModConfig.debugMode) {
            client.player.sendMessage(
                Text.literal("[AI] Taking a short break..."), true);
        }
    }

    private void refreshVariation() {
        if (!mouseLocked) {
            yawVariation       = (float)(RANDOM.nextGaussian() * 4.5f);
            pitchVariation     = (float)(RANDOM.nextGaussian() * 2.5f);
            timingDriftFactor  = 0.88f + RANDOM.nextFloat() * 0.24f;
        } else {
            yawVariation = pitchVariation = 0f;
            timingDriftFactor = 1.0f;
        }
        variationRefreshTimer = 50 + RANDOM.nextInt(90);
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
        client.options.dropKey.setPressed(false);
        client.options.swapHandsKey.setPressed(false);
        client.options.pickItemKey.setPressed(false);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public boolean isPlaying()      { return playing; }
    public boolean isPaused()       { return paused; }
    public boolean isScanning()     { return scanning; }
    public boolean isMouseLocked()  { return mouseLocked; }
    public int getLoopCount()       { return loopCount; }
    public int getCurrentFrame()    { return currentFrameIndex; }
    public int getTotalFrames()     { return frames != null ? frames.size() : 0; }
    public boolean isTakingBreak()  { return takingBreak; }
}
