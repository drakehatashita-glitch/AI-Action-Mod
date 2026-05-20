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

    private static final char[] COMMON_TYPO_SWAPS = {
        'a', 'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p',
        's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'
    };

    public static CompletableFuture<Void> sendWithDelay(String message) {
        if (message == null || message.isBlank()) return CompletableFuture.completedFuture(null);

        String finalMessage = maybeApplyTypoAndCorrect(message);
        long delay = calculateDelay(finalMessage);

        return CompletableFuture.runAsync(() -> {
            try {
                // Occasionally insert a mid-thought pause
                if (RANDOM.nextFloat() < 0.15f && finalMessage.length() > 8) {
                    long halfDelay = delay / 2;
                    Thread.sleep(halfDelay + RANDOM.nextInt(300));
                    Thread.sleep(delay - halfDelay);
                } else {
                    Thread.sleep(delay);
                }
            } catch (InterruptedException ignored) {}
        }).thenRunAsync(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.execute(() -> {
                    try {
                        client.player.networkHandler.sendChatMessage(finalMessage);
                    } catch (Exception e) {
                        // Ignore send failures silently
                    }
                });
            }
        });
    }

    public static long calculateDelay(String message) {
        int charCount = message.length();

        // Base typing time with Gaussian variance
        double base = charCount * ModConfig.typingSpeedMsPerChar;
        double variance = RANDOM.nextGaussian() * ModConfig.typingSpeedVarianceMs * charCount * 0.08;

        // Think time before typing (reading comprehension simulation)
        int thinkTime = 400 + RANDOM.nextInt(600);

        // Occasional long pauses to simulate distraction
        int distraction = RANDOM.nextFloat() < 0.08f ? RANDOM.nextInt(1500) : 0;

        // Burst pattern: short messages typed faster, long ones slower
        double burstFactor = charCount < 5 ? 0.6 : (charCount > 30 ? 1.3 : 1.0);

        long total = (long) ((base + variance) * burstFactor) + thinkTime + distraction;
        return Math.max(500, total);
    }

    private static String maybeApplyTypoAndCorrect(String message) {
        if (message.length() < 4) return message;
        if (RANDOM.nextDouble() >= ModConfig.typoChance) return message;

        // Only apply typos to longer messages where it's plausible
        // The message is sent as-is (in a real client you'd send a "wrong" then backspace,
        // but since we only send one chat message, we simulate the corrected result)
        // A real typo correction means we still send the correct version,
        // but with extra delay to simulate the correction
        return message;
    }

    public static long calculateDelayForLength(int length) {
        return calculateDelay(" ".repeat(Math.max(1, length)));
    }
}
