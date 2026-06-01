package com.documind.controller;

import com.documind.services.OpenAIService;
import com.documind.services.S3Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final S3Service s3Service;
    private final OpenAIService openAIService;

    public DocumentController(S3Service s3Service, OpenAIService openAIService) {
        this.s3Service = s3Service;
        this.openAIService = openAIService;
    }

    // POST /api/documents/upload
    // Uploads file to S3, returns the S3 key for use in /analyze
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
        }
        try {
            String s3Key = s3Service.upload(file);
            return ResponseEntity.ok(Map.of(
                "s3Key", s3Key,
                "fileName", file.getOriginalFilename()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    // POST /api/documents/analyze
    // Downloads file from S3, extracts text, sends to OpenAI, returns full analysis
    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody Map<String, String> request) {
        String s3Key = request.get("s3Key");
        String question = request.get("question");

        if (s3Key == null || s3Key.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "s3Key is required"));
        }

        try {
            String text = s3Service.downloadAsText(s3Key);

            if (text.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No text found in document"));
            }

            String analysis = openAIService.analyze(text, question);
            return ResponseEntity.ok(Map.of("analysis", analysis));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
