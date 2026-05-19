package com.aibot.mod.ai;

import com.aibot.mod.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class TypingSimulator {
    private static final Random RANDOM = new Random();

    public static CompletableFuture<Void> sendWithDelay(String message) {
        long delay = calculateDelay(message);
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {}
        }).thenRunAsync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.execute(() -> {
                    try {
                        client.player.networkHandler.sendChatMessage(message);
                    } catch (Exception e) {
                        // Ignore send failures
                    }
                });
            }
        });
    }

    public static long calculateDelay(String message) {
        int base = message.length() * ModConfig.typingSpeedMsPerChar;
        int variance = (int) (RANDOM.nextGaussian() * ModConfig.typingSpeedVarianceMs * message.length() * 0.1);
        int extra = RANDOM.nextInt(800) + 200;
        return Math.max(600, base + variance + extra);
    }
}
