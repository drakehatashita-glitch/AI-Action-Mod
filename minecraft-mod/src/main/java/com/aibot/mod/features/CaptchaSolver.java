package com.aibot.mod.features;

import com.aibot.mod.AiMod;
import com.aibot.mod.ai.OllamaClient;
import com.aibot.mod.ai.TypingSimulator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public class CaptchaSolver {
    // Alphanumeric code pattern (4-10 chars)
    private static final Pattern CODE_PATTERN = Pattern.compile("\\b([A-Z0-9]{4,10})\\b");

    // Numeric math: 3 + 5, 7 - 2, 4 * 3, 6 x 2
    private static final Pattern MATH_PATTERN = Pattern.compile("(\\d+)\\s*([+\\-*x×÷/])\\s*(\\d+)");

    // Word math: "two plus three", "seven minus four", etc.
    private static final Pattern WORD_MATH_PATTERN = Pattern.compile(
        "(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\s+" +
        "(plus|minus|times|multiplied by|divided by|add|subtract)\\s+" +
        "(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)",
        Pattern.CASE_INSENSITIVE
    );

    // Roman numerals
    private static final Pattern ROMAN_MATH_PATTERN = Pattern.compile(
        "([IVXLCDM]+)\\s*([+\\-*x])\\s*([IVXLCDM]+)",
        Pattern.CASE_INSENSITIVE
    );

    // /captcha <answer> style prompt
    private static final Pattern CAPTCHA_COMMAND_PATTERN = Pattern.compile(
        "/captcha\\s+(\\S+)", Pattern.CASE_INSENSITIVE
    );

    private static final String[] CAPTCHA_TRIGGERS = {
        "captcha", "verify", "enter the code", "type the code",
        "complete the captcha", "anti-bot", "prove you are human",
        "enter code", "verification code", "type below", "solve",
        "anti cheat", "bot check", "please type", "what is", "answer:",
        "type the answer", "respond with", "security check", "human check",
        "type this", "enter this", "confirm you", "verification"
    };

    private static final Map<String, Integer> WORD_TO_NUM = Map.ofEntries(
        Map.entry("zero", 0), Map.entry("one", 1), Map.entry("two", 2),
        Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5),
        Map.entry("six", 6), Map.entry("seven", 7), Map.entry("eight", 8),
        Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11),
        Map.entry("twelve", 12)
    );

    public boolean handleChatCaptcha(Text message) {
        String rawText = message.getString();
        String lower = rawText.toLowerCase();

        if (!containsCaptchaTrigger(lower)) return false;

        AiMod.LOGGER.info("Captcha detected: {}", rawText);

        // Try each solver in priority order

        // 1. /captcha <answer> style
        Matcher cmdMatcher = CAPTCHA_COMMAND_PATTERN.matcher(rawText);
        if (cmdMatcher.find()) {
            String answer = cmdMatcher.group(1);
            AiMod.LOGGER.info("Direct captcha command answer: {}", answer);
            TypingSimulator.sendWithDelay(answer);
            return true;
        }

        // 2. Numeric math
        Matcher mathMatcher = MATH_PATTERN.matcher(rawText);
        if (mathMatcher.find()) {
            int result = solveNumericMath(mathMatcher);
            if (result != Integer.MIN_VALUE) {
                AiMod.LOGGER.info("Solved numeric math captcha: {}", result);
                TypingSimulator.sendWithDelay(String.valueOf(result));
                return true;
            }
        }

        // 3. Word math ("two plus three")
        Matcher wordMatcher = WORD_MATH_PATTERN.matcher(lower);
        if (wordMatcher.find()) {
            int result = solveWordMath(wordMatcher);
            if (result != Integer.MIN_VALUE) {
                AiMod.LOGGER.info("Solved word math captcha: {}", result);
                TypingSimulator.sendWithDelay(String.valueOf(result));
                return true;
            }
        }

        // 4. Roman numeral math
        Matcher romanMatcher = ROMAN_MATH_PATTERN.matcher(rawText);
        if (romanMatcher.find()) {
            int result = solveRomanMath(romanMatcher);
            if (result != Integer.MIN_VALUE) {
                AiMod.LOGGER.info("Solved roman numeral captcha: {}", result);
                TypingSimulator.sendWithDelay(String.valueOf(result));
                return true;
            }
        }

        // 5. Alphanumeric code in the message
        Matcher codeMatcher = CODE_PATTERN.matcher(rawText);
        if (codeMatcher.find()) {
            String code = codeMatcher.group(1);
            // Skip common false positives
            if (!isCommonWord(code)) {
                AiMod.LOGGER.info("Found captcha code: {}", code);
                TypingSimulator.sendWithDelay(code);
                return true;
            }
        }

        // 6. Fall back to Ollama AI
        String prompt = "This is a Minecraft server chat captcha message: \""
                + rawText + "\". What should I type to complete it? "
                + "Reply with ONLY the exact answer to type, nothing else. "
                + "If it's a math problem, solve it. If it's a code, repeat it.";
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

        AiMod.LOGGER.info("Image captcha detected, analyzing with vision model...");

        String prompt = "This is a screenshot of a Minecraft server captcha. "
                + "What text, code, or number do you see that I need to type? "
                + "Reply with ONLY the code or answer to type, nothing else.";

        OllamaClient.describeImage(screenshotBytes, prompt).thenAccept(response -> {
            if (response != null && !response.isBlank()) {
                String answer = response.trim().split("\\n")[0].trim();
                AiMod.LOGGER.info("Image captcha answer: {}", answer);
                TypingSimulator.sendWithDelay(answer);
            }
        });
    }

    private boolean containsCaptchaTrigger(String lower) {
        for (String trigger : CAPTCHA_TRIGGERS) {
            if (lower.contains(trigger)) return true;
        }
        return false;
    }

    private int solveNumericMath(Matcher m) {
        try {
            int a = Integer.parseInt(m.group(1));
            String op = m.group(2);
            int b = Integer.parseInt(m.group(3));
            return switch (op) {
                case "+", "add" -> a + b;
                case "-", "minus" -> a - b;
                case "*", "x", "×", "times" -> a * b;
                case "/", "÷" -> b != 0 ? a / b : Integer.MIN_VALUE;
                default -> Integer.MIN_VALUE;
            };
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private int solveWordMath(Matcher m) {
        try {
            int a = WORD_TO_NUM.getOrDefault(m.group(1).toLowerCase(), Integer.MIN_VALUE);
            int b = WORD_TO_NUM.getOrDefault(m.group(3).toLowerCase(), Integer.MIN_VALUE);
            if (a == Integer.MIN_VALUE || b == Integer.MIN_VALUE) return Integer.MIN_VALUE;

            String op = m.group(2).toLowerCase();
            return switch (op) {
                case "plus", "add" -> a + b;
                case "minus", "subtract" -> a - b;
                case "times", "multiplied by" -> a * b;
                case "divided by" -> b != 0 ? a / b : Integer.MIN_VALUE;
                default -> Integer.MIN_VALUE;
            };
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    private int solveRomanMath(Matcher m) {
        try {
            int a = romanToInt(m.group(1).toUpperCase());
            int b = romanToInt(m.group(3).toUpperCase());
            String op = m.group(2);
            return switch (op) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*", "x", "X" -> a * b;
                default -> Integer.MIN_VALUE;
            };
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    private int romanToInt(String s) {
        Map<Character, Integer> map = Map.of(
            'I', 1, 'V', 5, 'X', 10, 'L', 50, 'C', 100, 'D', 500, 'M', 1000
        );
        int result = 0;
        for (int i = 0; i < s.length(); i++) {
            int curr = map.getOrDefault(s.charAt(i), 0);
            int next = i + 1 < s.length() ? map.getOrDefault(s.charAt(i + 1), 0) : 0;
            if (curr < next) result -= curr;
            else result += curr;
        }
        return result;
    }

    private boolean isCommonWord(String code) {
        // Filter codes that look like common Minecraft words (false positives)
        String lower = code.toLowerCase();
        return lower.equals("chat") || lower.equals("type") || lower.equals("code")
                || lower.equals("server") || lower.equals("minecraft") || lower.equals("join")
                || lower.equals("here") || lower.equals("true") || lower.equals("false");
    }
}
