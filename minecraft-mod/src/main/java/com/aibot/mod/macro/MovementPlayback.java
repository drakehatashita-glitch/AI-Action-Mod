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

    private float yawVariation = 0f;
    private float pitchVariation = 0f;
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

        variationRefreshTimer--;
        if (variationRefreshTimer <= 0) {
            refreshVariation();
        }

        ActionFrame frame = frames.get(currentFrameIndex);
        applyFrame(client, frame);

        currentFrameTick++;
        if (currentFrameTick >= frame.tickDuration) {
            currentFrameTick = 0;
            currentFrameIndex++;

            if (shouldAddMicroPause()) {
                extraPauseTicks = RANDOM.nextInt(3) + 1;
            }

            if (currentFrameIndex >= frames.size()) {
                loopCount++;
                if (looping) {
                    currentFrameIndex = 0;
                    refreshVariation();
                    int loopPause = 5 + RANDOM.nextInt(15);
                    extraPauseTicks = loopPause;
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
        client.options.sprintKey.setPressed(frame.sprinting || (frame.forward && RANDOM.nextFloat() < 0.95f && shouldSprint(frame)));
        client.options.attackKey.setPressed(frame.attacking);
        client.options.useKey.setPressed(frame.using);

        float newYaw = frame.yaw + yawVariation + (RANDOM.nextFloat() - 0.5f) * 0.3f;
        float newPitch = frame.pitch + pitchVariation + (RANDOM.nextFloat() - 0.5f) * 0.2f;
        newPitch = Math.max(-90f, Math.min(90f, newPitch));

        player.setYaw(newYaw);
        player.setPitch(newPitch);
    }

    private void refreshVariation() {
        yawVariation = (RANDOM.nextFloat() - 0.5f) * 4f;
        pitchVariation = (RANDOM.nextFloat() - 0.5f) * 2f;
        variationRefreshTimer = 40 + RANDOM.nextInt(80);
    }

    private boolean shouldAddMicroPause() {
        return RANDOM.nextInt(100) < 3;
    }

    private boolean shouldSprint(ActionFrame frame) {
        return frame.forward && !frame.sneaking;
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
