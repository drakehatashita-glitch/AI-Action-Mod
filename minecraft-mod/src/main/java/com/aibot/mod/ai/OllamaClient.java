package com.aibot.mod.ai;

import com.aibot.mod.AiMod;
import com.aibot.mod.config.ModConfig;
import com.google.gson.Gson;
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

public class OllamaClient {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static CompletableFuture<String> ask(String prompt) {
        return askWithContext(prompt, null);
    }

    public static CompletableFuture<String> askWithContext(String prompt, String systemContext) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("model", ModConfig.chatModel);

                String fullPrompt = prompt;
                if (systemContext != null) {
                    fullPrompt = systemContext + "\n\n" + prompt;
                }
                body.addProperty("prompt", fullPrompt);
                body.addProperty("stream", false);

                JsonObject options = new JsonObject();
                options.addProperty("temperature", 0.8);
                options.addProperty("num_predict", 150);
                body.add("options", options);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(ModConfig.ollamaUrl + "/api/generate"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    return json.get("response").getAsString().trim();
                } else {
                    AiMod.LOGGER.error("Ollama returned status {}", response.statusCode());
                    return null;
                }
            } catch (Exception e) {
                AiMod.LOGGER.error("Failed to contact Ollama: {}", e.getMessage());
                return null;
            }
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

                com.google.gson.JsonArray images = new com.google.gson.JsonArray();
                images.add(base64Image);
                body.add("images", images);

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
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
