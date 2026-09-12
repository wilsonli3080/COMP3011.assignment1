package au.edu.adelaide.sttassignment1.controller;

import au.edu.adelaide.sttassignment1.service.MetricsService;
import au.edu.adelaide.sttassignment1.service.OpenAiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class TranscriptionController {

    private final OpenAiService openAiService;
    private final MetricsService metricsService;

    public TranscriptionController(OpenAiService openAiService, MetricsService metricsService) {
        this.openAiService = openAiService;
        this.metricsService = metricsService;
    }

    @PostMapping("/transcribe")
    public ResponseEntity<Map<String, Object>> transcribe(@RequestParam("file") MultipartFile file) {
        metricsService.incrementRequests();
        Instant start = Instant.now();
        try {
            OpenAiService.TranscriptionResult result = openAiService.transcribe(file);
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            metricsService.incrementTranscriptions();
            metricsService.addLatency(latencyMs);
            metricsService.addInputTokens(result.inputTokens());
            metricsService.addOutputTokens(result.outputTokens());

            Map<String, Object> resp = new HashMap<>();
            resp.put("text", result.text());
            resp.put("latencyMs", latencyMs);
            resp.put("inputTokens", result.inputTokens());
            resp.put("outputTokens", result.outputTokens());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }
}
