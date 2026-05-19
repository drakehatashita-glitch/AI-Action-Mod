package com.aibot.mod.features;

import com.aibot.mod.AiMod;
import com.aibot.mod.AiModClient;
import com.aibot.mod.ai.LearnedResponseManager;
import com.aibot.mod.ai.OllamaClient;
import com.aibot.mod.keybind.ModKeyBindings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class FeatureManager {
    private final AutoFishFeature autoFish = new AutoFishFeature();
    private final AutoAttackFeature autoAttack = new AutoAttackFeature();
    private final PlayerDetectionFeature playerDetection = new PlayerDetectionFeature();
    private final HumanMouseMovement mouseMovement = new HumanMouseMovement();
    private final LearnedResponseManager learnedResponses = new LearnedResponseManager();
    private final CaptchaSolver captchaSolver = new CaptchaSolver();
    private ChatHandler chatHandler;

    private boolean allActive = false;

    public void init() {
        learnedResponses.load();
        chatHandler = new ChatHandler(learnedResponses, mouseMovement);

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            onChatMessage(message);
        });

        OllamaClient.isAvailable();
        AiMod.LOGGER.info("Feature manager initialized. Ollama check complete.");
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null) return;

        handleKeyBindings(client);

        if (autoFish.isActive()) {
            autoFish.tick();
        }

        if (autoAttack.isActive()) {
            autoAttack.tick();
        }

        playerDetection.tick();
    }

    private void handleKeyBindings(MinecraftClient client) {
        while (ModKeyBindings.toggleFish.wasPressed()) {
            boolean newState = !autoFish.isActive();
            autoFish.setActive(newState);
            sendActionBar(client, "Auto-Fish: " + (newState ? "ON" : "OFF"));
        }

        while (ModKeyBindings.toggleAttack.wasPressed()) {
            boolean newState = !autoAttack.isActive();
            autoAttack.setActive(newState);
            sendActionBar(client, "Auto-Attack: " + (newState ? "ON" : "OFF"));
        }

        while (ModKeyBindings.toggleAll.wasPressed()) {
            allActive = !allActive;
            autoFish.setActive(allActive);
            autoAttack.setActive(allActive);
            sendActionBar(client, "AI Mod: " + (allActive ? "ALL ON" : "ALL OFF"));
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
            client.player.sendMessage(Text.literal("[AI] " + msg), true);
        }
    }

    public AutoFishFeature getAutoFish() { return autoFish; }
    public AutoAttackFeature getAutoAttack() { return autoAttack; }
    public LearnedResponseManager getLearnedResponses() { return learnedResponses; }
}
