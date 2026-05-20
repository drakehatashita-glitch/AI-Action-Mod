package com.aibot.mod.features;

import com.aibot.mod.AiMod;
import com.aibot.mod.ai.LearnedResponseManager;
import com.aibot.mod.ai.OllamaClient;
import com.aibot.mod.config.ModConfig;
import com.aibot.mod.gui.PromptScreen;
import com.aibot.mod.gui.RecordingManagerScreen;
import com.aibot.mod.keybind.ModKeyBindings;
import com.aibot.mod.macro.AiPromptExecutor;
import com.aibot.mod.macro.MovementPlayback;
import com.aibot.mod.macro.MovementRecorder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class FeatureManager {
    private final AutoFishFeature autoFish = new AutoFishFeature();
    private final AutoAttackFeature autoAttack = new AutoAttackFeature();
    private final AutoSellFeature autoSell = new AutoSellFeature();
    private final PlayerDetectionFeature playerDetection = new PlayerDetectionFeature();
    private final HumanMouseMovement mouseMovement = new HumanMouseMovement();
    private final LearnedResponseManager learnedResponses = new LearnedResponseManager();
    private final CaptchaSolver captchaSolver = new CaptchaSolver();
    private final MovementRecorder recorder = new MovementRecorder();
    private final MovementPlayback playback = new MovementPlayback();
    private final AiPromptExecutor promptExecutor;
    private ChatHandler chatHandler;

    private boolean allActive = false;

    // Anti-AFK ticker
    private int antiAfkTimer = 0;

    public FeatureManager() {
        promptExecutor = new AiPromptExecutor(playback);
    }

    public void init() {
        learnedResponses.load();
        chatHandler = new ChatHandler(learnedResponses, mouseMovement);
        chatHandler.setPlayerDetection(playerDetection);

        playerDetection.setPlayback(playback);
        promptExecutor.setRecorder(recorder);

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                onChatMessage(message));

        // Check Ollama availability in background
        new Thread(() -> OllamaClient.isAvailable()).start();

        AiMod.LOGGER.info("Feature manager initialized. Keys: [K] fish, [L] attack, [;] all, [R] record, [P] play, [G] AI prompt, [J] recordings");
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null) return;

        handleKeyBindings(client);

        if (autoFish.isActive()) autoFish.tick();
        if (autoAttack.isActive()) autoAttack.tick();
        autoSell.tick();
        playerDetection.tick();
        recorder.tick();
        playback.tick();
        chatHandler.tick();

        // Anti-AFK: occasional subtle movement when idle
        if (ModConfig.antiAfkEnabled && !autoFish.isActive() && !autoAttack.isActive()
                && !playback.isPlaying()) {
            tickAntiAfk(client);
        }
    }

    private void tickAntiAfk(MinecraftClient client) {
        antiAfkTimer++;
        if (antiAfkTimer < ModConfig.antiAfkIntervalTicks) return;
        antiAfkTimer = 0;

        // Random subtle movement: small yaw rotation
        if (client.player != null) {
            float nudge = (float) (Math.random() - 0.5) * 8f;
            client.execute(() -> {
                if (client.player != null) {
                    client.player.setYaw(client.player.getYaw() + nudge);
                }
            });
            AiMod.LOGGER.debug("Anti-AFK nudge applied");
        }
    }

    private void handleKeyBindings(MinecraftClient client) {
        while (ModKeyBindings.toggleFish.wasPressed()) {
            boolean newState = !autoFish.isActive();
            autoFish.setActive(newState);
            sendActionBar(client, "Auto-Fish: " + (newState ? "§aON" : "§cOFF"));
        }

        while (ModKeyBindings.toggleAttack.wasPressed()) {
            boolean newState = !autoAttack.isActive();
            autoAttack.setActive(newState);
            sendActionBar(client, "Auto-Attack: " + (newState ? "§aON" : "§cOFF"));
        }

        while (ModKeyBindings.toggleAll.wasPressed()) {
            allActive = !allActive;
            autoFish.setActive(allActive);
            autoAttack.setActive(allActive);
            autoSell.setActive(allActive);
            sendActionBar(client, "AI Mod: " + (allActive ? "§aALL ON" : "§cALL OFF"));
        }

        while (ModKeyBindings.toggleRecord.wasPressed()) {
            if (recorder.isRecording()) {
                recorder.stopRecording();
                if (client.currentScreen == null) {
                    client.setScreen(new PromptScreen(promptExecutor, recorder, true));
                }
            } else {
                if (playback.isPlaying()) playback.stop();
                recorder.startRecording("last");
            }
        }

        while (ModKeyBindings.togglePlayback.wasPressed()) {
            if (playback.isPlaying() || playback.isScanning()) {
                playback.stop();
                playback.stopScan();
            } else {
                var frames = recorder.load("last");
                if (frames != null && !frames.isEmpty()) {
                    playback.setMouseLocked(false);
                    playback.start(frames, true);
                } else {
                    sendActionBar(client, "No recording found — press [R] to record first.");
                }
            }
        }

        while (ModKeyBindings.openPromptGui.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new PromptScreen(promptExecutor));
            }
        }

        while (ModKeyBindings.openRecordingManager.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new RecordingManagerScreen(recorder, playback));
            }
        }
    }

    private void onGameMessage(Text message, boolean overlay) {
        if (overlay) return;
        captchaSolver.handleChatCaptcha(message);
        chatHandler.onChatMessage(message);
    }

    private void onChatMessage(Text message) {
        captchaSolver.handleChatCaptcha(message);
        chatHandler.onChatMessage(message);
    }

    private void sendActionBar(MinecraftClient client, String msg) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§7[AI] " + msg), true);
        }
    }

    public AutoFishFeature getAutoFish() { return autoFish; }
    public AutoAttackFeature getAutoAttack() { return autoAttack; }
    public AutoSellFeature getAutoSell() { return autoSell; }
    public LearnedResponseManager getLearnedResponses() { return learnedResponses; }
    public MovementRecorder getRecorder() { return recorder; }
    public MovementPlayback getPlayback() { return playback; }
    public AiPromptExecutor getPromptExecutor() { return promptExecutor; }
    public PlayerDetectionFeature getPlayerDetection() { return playerDetection; }
}
