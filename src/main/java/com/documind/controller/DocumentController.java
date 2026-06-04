package com.documind.controller;

import com.documind.services.ClaudeService;
import com.documind.services.S3Service;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final S3Service s3Service;
    private final ClaudeService claudeService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public DocumentController(S3Service s3Service, ClaudeService claudeService) {
        this.s3Service = s3Service;
        this.claudeService = claudeService;
    }

    // ── POST /api/documents/upload ────────────────────────────────────────────
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
        }
        System.out.println("----Uploading from local----------------");
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

    // ── POST /api/documents/analyze ───────────────────────────────────────────
    // Normal flow — waits for full response, returns JSON
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

            String analysis = claudeService.analyze(text, question);
            return ResponseEntity.ok(Map.of("analysis", analysis));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── POST /api/documents/analyze/stream ────────────────────────────────────
    // Streaming flow — returns SSE, frontend receives tokens as they arrive
    @PostMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeStream(@RequestBody Map<String, String> request) {
        String s3Key = request.get("s3Key");
        String question = request.get("question");

        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        executor.submit(() -> {
            try {
                String text = s3Service.downloadAsText(s3Key);

                if (text == null || text.isBlank()) {
                    emitter.send(SseEmitter.event().data("[ERROR] No text found in document"));
                    emitter.complete();
                    return;
                }

                // streams each token directly to the frontend via emitter
                claudeService.streamAnalyze(text, question, emitter);

            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data("[ERROR] " + e.getMessage()));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });

        return emitter;
    }


    //fetch all documents
    @GetMapping("/all")
    public ResponseEntity<?> getAllDocuments() {
        try {
            List<Map<String, String>> documents = s3Service.listDocuments();
            return ResponseEntity.ok(Map.of("documents", documents));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
