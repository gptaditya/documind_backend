package com.documind.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class OpenAIService {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.api.model}")
    private String model;

    private final WebClient webClient = WebClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    // Calls OpenAI and returns the full analysis as a plain String
    public String analyze(String documentText, String question) {

        String truncated = documentText.length() > 12000
                ? documentText.substring(0, 12000) + "\n[Document truncated...]"
                : documentText;

        String prompt = (question != null && !question.isBlank())
                ? question
                : "Summarize this document and list the key points.";

        Map<String, Object> body = Map.of(
            "model", model,
            "stream", false,      // false = wait for full response, return it all at once
            "messages", List.of(
                Map.of("role", "system", "content",
                    "You are a document analysis assistant. Analyze the document and answer clearly."),
                Map.of("role", "user", "content",
                    "Document:\n\n" + truncated + "\n\nQuestion: " + prompt)
            ),
            "max_tokens", 1000,
            "temperature", 0.3
        );

        // .block() waits for the full response — turns async WebClient into synchronous call
        String responseBody = webClient.post()
                .uri(apiUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)  // Mono = single value (not a stream)
                .block();                  // block the thread until response arrives

        // Parse JSON response: choices[0].message.content
        try {
            JsonNode root = mapper.readTree(responseBody);
            return root.path("choices").get(0)
                       .path("message")
                       .path("content")
                       .asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response: " + e.getMessage());
        }
    }
}
