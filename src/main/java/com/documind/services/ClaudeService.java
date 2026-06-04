package com.documind.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClaudeService {

    @Value("${openrouter.api.key}")
    private String apiKey;

    @Value("${openrouter.api.url}")
    private String apiUrl;

    @Value("${openrouter.api.model}")
    private String model;

    // RestClient for normal (non-streaming) calls
    private final RestClient restClient = RestClient.create();

    // WebClient for streaming — uses subscribe() not block(), so no hanging
    private final WebClient webClient = WebClient.create();

    private final ObjectMapper mapper = new ObjectMapper();

    // ── NORMAL (non-streaming) ────────────────────────────────────────────────
    public String analyze(String documentText, String question) {

        String truncated = documentText.length() > 12000
                ? documentText.substring(0, 12000) + "\n[Document truncated...]"
                : documentText;

        String prompt = (question != null && !question.isBlank())
                ? question
                : "Summarize this document and list the key points.";

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("stream", false);
        body.put("messages", List.of(
            Map.of("role", "system",
                   "content", "You are a document analysis assistant. Analyze the document and answer clearly and concisely."),
            Map.of("role", "user",
                   "content", "Document:\n\n" + truncated + "\n\nQuestion: " + prompt)
        ));

        try {
            String responseBody = restClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://documind.xadisri.in")
                    .header("X-Title", "DocuMind")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(responseBody);
            return root.path("choices").get(0)
                       .path("message")
                       .path("content")
                       .asText();

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new RuntimeException("AI API error: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to call AI: " + e.getMessage());
        }
    }

    // ── STREAMING ────────────────────────────────────────────────────────────
    // Uses subscribe() not block() — pushes each token to the frontend via SseEmitter
    public void streamAnalyze(String documentText, String question, SseEmitter emitter) {

        String truncated = documentText.length() > 12000
                ? documentText.substring(0, 12000) + "\n[Document truncated...]"
                : documentText;

        String prompt = (question != null && !question.isBlank())
                ? question
                : "Summarize this document and list the key points.";

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("stream", true);   // enable streaming
        body.put("messages", List.of(
            Map.of("role", "system",
                   "content", "You are a document analysis assistant. Analyze the document and answer clearly and concisely."),
            Map.of("role", "user",
                   "content", "Document:\n\n" + truncated + "\n\nQuestion: " + prompt)
        ));

        webClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("HTTP-Referer", "https://documind.xadisri.in")
                .header("X-Title", "DocuMind")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .subscribe(
                    // called for each SSE chunk from OpenRouter
                    chunk -> {
                        try {
                            // each chunk looks like: "data: {...}" or "data: [DONE]"
                            String data = chunk.replace("data: ", "").trim();

                            if (data.equals("[DONE]")) {
                                emitter.complete();
                                return;
                            }

                            JsonNode node = mapper.readTree(data);
                            JsonNode content = node.path("choices").get(0)
                                                   .path("delta")
                                                   .path("content");

                            if (!content.isMissingNode() && !content.isNull()) {
                                emitter.send(SseEmitter.event().data(content.asText()));
                            }
                        } catch (Exception ignored) {}
                    },
                    // called if stream hits an error
                    error -> {
                        System.out.println("Stream error: " + error.getMessage());
                        try {
                            emitter.send(SseEmitter.event().data("[ERROR] " + error.getMessage()));
                            emitter.complete();
                        } catch (Exception ignored) {}
                    },
                    // called when stream ends naturally
                    emitter::complete
                );
    }
}
