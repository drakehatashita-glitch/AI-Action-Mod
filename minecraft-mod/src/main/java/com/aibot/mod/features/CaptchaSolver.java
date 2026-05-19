package com.aibot.mod.features;

import com.aibot.mod.AiMod;
import com.aibot.mod.ai.OllamaClient;
import com.aibot.mod.ai.TypingSimulator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public class CaptchaSolver {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9]{4,8}");
    private static final Pattern MATH_PATTERN = Pattern.compile("(\\d+)\\s*([+\\-*x])\\s*(\\d+)");

    private static final String[] CAPTCHA_TRIGGERS = {
            "captcha", "verify", "enter the code", "type the code",
            "complete the captcha", "anti-bot", "prove you are human",
            "enter code", "verification code", "type below"
    };

    public boolean handleChatCaptcha(Text message) {
        String text = message.getString().toLowerCase();

        boolean isCaptcha = false;
        for (String trigger : CAPTCHA_TRIGGERS) {
            if (text.contains(trigger)) {
                isCaptcha = true;
                break;
            }
        }

        if (!isCaptcha) return false;

        AiMod.LOGGER.info("Captcha detected in chat: {}", message.getString());

        String rawText = message.getString();

        Matcher mathMatcher = MATH_PATTERN.matcher(rawText);
        if (mathMatcher.find()) {
            int a = Integer.parseInt(mathMatcher.group(1));
            String op = mathMatcher.group(2);
            int b = Integer.parseInt(mathMatcher.group(3));
            int result = switch (op) {
                case "+", "plus" -> a + b;
                case "-", "minus" -> a - b;
                case "*", "x", "times" -> a * b;
                default -> -1;
            };
            if (result != -1) {
                AiMod.LOGGER.info("Solved math captcha: {} {} {} = {}", a, op, b, result);
                TypingSimulator.sendWithDelay(String.valueOf(result));
                return true;
            }
        }

        Matcher codeMatcher = CODE_PATTERN.matcher(rawText);
        if (codeMatcher.find()) {
            String code = codeMatcher.group();
            AiMod.LOGGER.info("Found captcha code: {}", code);
            TypingSimulator.sendWithDelay(code);
            return true;
        }

        String prompt = "This is a Minecraft chat message containing a captcha: \""
                + rawText + "\". What should I type to complete the captcha? Reply with ONLY the answer, nothing else.";
        OllamaClient.ask(prompt).thenAccept(response -> {
            if (response != null && !response.isBlank()) {
                String answer = response.trim().split("\\s+")[0];
                AiMod.LOGGER.info("AI solved captcha: {}", answer);
                TypingSimulator.sendWithDelay(answer);
            }
        });

        return true;
    }

    public void handleImageCaptcha(byte[] screenshotBytes) {
        if (screenshotBytes == null || screenshotBytes.length == 0) return;

        AiMod.LOGGER.info("Image captcha detected, analyzing with LLaVA...");

        String prompt = "This is a screenshot of a Minecraft captcha. What text or code do you see that I need to type? Reply with ONLY the code or text to type, nothing else.";

        OllamaClient.describeImage(screenshotBytes, prompt).thenAccept(response -> {
            if (response != null && !response.isBlank()) {
                String answer = response.trim().split("\\n")[0].trim();
                AiMod.LOGGER.info("Image captcha answer: {}", answer);
                TypingSimulator.sendWithDelay(answer);
            }
        });
    }
}
