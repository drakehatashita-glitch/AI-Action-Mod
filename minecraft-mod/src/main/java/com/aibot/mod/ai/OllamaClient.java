package com.aibot.mod.ai;

import com.aibot.mod.AiMod;
import com.aibot.mod.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class OllamaClient {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 500;

    public static CompletableFuture<String> ask(String prompt) {
        return askWithContext(prompt, null);
    }

    public static CompletableFuture<String> askWithContext(String prompt, String systemContext) {
        return askWithOptions(prompt, systemContext, ModConfig.temperature, ModConfig.maxTokens);
    }

    public static CompletableFuture<String> askWithOptions(String prompt, String systemContext,
                                                            double temperature, int maxTokens) {
        return CompletableFuture.supplyAsync(() -> {
            Exception lastException = null;
            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                if (attempt > 0) {
                    try { Thread.sleep(RETRY_DELAY_MS * attempt); } catch (InterruptedException ignored) {}
                    AiMod.LOGGER.info("Retrying Ollama request (attempt {})", attempt + 1);
                }
                try {
                    JsonObject body = new JsonObject();
                    body.addProperty("model", ModConfig.chatModel);

                    String fullPrompt = systemContext != null ? systemContext + "\n\n" + prompt : prompt;
                    body.addProperty("prompt", fullPrompt);
                    body.addProperty("stream", false);

                    JsonObject options = new JsonObject();
                    options.addProperty("temperature", temperature);
                    options.addProperty("num_predict", maxTokens);
                    options.addProperty("top_p", 0.9);
                    options.addProperty("repeat_penalty", 1.1);
                    body.add("options", options);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(ModConfig.ollamaUrl + "/api/generate"))
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(ModConfig.ollamaTimeoutSeconds))
                            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                            .build();

                    HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                        String result = json.get("response").getAsString().trim();
                        if (!result.isEmpty()) return result;
                    } else {
                        AiMod.LOGGER.error("Ollama returned status {} on attempt {}", response.statusCode(), attempt + 1);
                    }
                } catch (Exception e) {
                    lastException = e;
                    AiMod.LOGGER.error("Failed to contact Ollama (attempt {}): {}", attempt + 1, e.getMessage());
                }
            }
            if (lastException != null) {
                AiMod.LOGGER.error("All Ollama retries exhausted", lastException);
            }
            return null;
        });
    }

    public static CompletableFuture<String> askWithHistory(List<ConversationTurn> history, String newPrompt, String systemContext) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder fullPrompt = new StringBuilder();
                if (systemContext != null) {
                    fullPrompt.append(systemContext).append("\n\n");
                }
                for (ConversationTurn turn : history) {
                    fullPrompt.append("Player: ").append(turn.userMessage).append("\n");
                    fullPrompt.append("You: ").append(turn.botResponse).append("\n");
                }
                fullPrompt.append("Player: ").append(newPrompt).append("\nYou:");

                JsonObject body = new JsonObject();
                body.addProperty("model", ModConfig.chatModel);
                body.addProperty("prompt", fullPrompt.toString());
                body.addProperty("stream", false);

                JsonObject options = new JsonObject();
                options.addProperty("temperature", ModConfig.temperature);
                options.addProperty("num_predict", ModConfig.maxTokens);
                options.addProperty("stop", "Player:");
                body.add("options", options);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(ModConfig.ollamaUrl + "/api/generate"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(ModConfig.ollamaTimeoutSeconds))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    return json.get("response").getAsString().trim();
                }
            } catch (Exception e) {
                AiMod.LOGGER.error("Failed to contact Ollama (history): {}", e.getMessage());
            }
            return null;
        });
    }

    public static CompletableFuture<String> describeImage(byte[] imageBytes, String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);

                JsonObject body = new JsonObject();
                body.addProperty("model", ModConfig.visionModel);
                body.addProperty("prompt", prompt);
                body.addProperty("stream", false);

                JsonArray images = new JsonArray();
                images.add(base64Image);
                body.add("images", images);

                JsonObject options = new JsonObject();
                options.addProperty("temperature", 0.1);
                options.addProperty("num_predict", 50);
                body.add("options", options);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(ModConfig.ollamaUrl + "/api/generate"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    return json.get("response").getAsString().trim();
                } else {
                    AiMod.LOGGER.error("Ollama vision returned status {}", response.statusCode());
                    return null;
                }
            } catch (Exception e) {
                AiMod.LOGGER.error("Failed to contact Ollama vision: {}", e.getMessage());
                return null;
            }
        });
    }

    public static boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ModConfig.ollamaUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            boolean available = response.statusCode() == 200;
            if (available) {
                AiMod.LOGGER.info("Ollama is available at {}", ModConfig.ollamaUrl);
            } else {
                AiMod.LOGGER.warn("Ollama not available (status {}). Chat AI will be limited.", response.statusCode());
            }
            return available;
        } catch (Exception e) {
            AiMod.LOGGER.warn("Ollama not reachable: {}. Chat AI will use learned responses only.", e.getMessage());
            return false;
        }
    }

    public static class ConversationTurn {
        public final String userMessage;
        public final String botResponse;

        public ConversationTurn(String userMessage, String botResponse) {
            this.userMessage = userMessage;
            this.botResponse = botResponse;
        }
    }
}
