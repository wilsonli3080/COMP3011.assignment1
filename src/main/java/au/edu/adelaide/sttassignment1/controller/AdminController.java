package au.edu.adelaide.sttassignment1.controller;

import au.edu.adelaide.sttassignment1.service.MetricsService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AdminController {

    private final MetricsService metricsService;
    private final ApplicationContext ctx;

    public AdminController(MetricsService metricsService, ApplicationContext ctx) {
        this.metricsService = metricsService;
        this.ctx = ctx;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> m = new HashMap<>();
        m.put("status", "ok");
        m.put("uptimeSeconds", Duration.between(metricsService.getStart(), Instant.now()).getSeconds());
        return ResponseEntity.ok(m);
    }

    @GetMapping("/admin/uptime")
    public ResponseEntity<Map<String, Object>> uptime() {
        Instant now = Instant.now();
        Instant start = metricsService.getStart();
        double uptimeSeconds = Duration.between(start, now).toNanos() / 1_000_000_000.0;

        Map<String, Object> m = new HashMap<>();
        m.put("utcServerStart", start.toString());
        m.put("utcNow", now.toString());
        m.put("serverUptimeSeconds", uptimeSeconds);
        return ResponseEntity.ok(m);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> m = new HashMap<>();
        long totalRequests = metricsService.getTotalRequests();
        long totalTrans = metricsService.getTotalTranscriptions();
        long totalLatency = metricsService.getTotalLatencyMs();
        double avgLatency = totalTrans == 0 ? 0.0 : ((double) totalLatency) / totalTrans;
        m.put("uptimeSeconds", Duration.between(metricsService.getStart(), Instant.now()).getSeconds());
        m.put("totalRequests", totalRequests);
        m.put("totalTranscriptions", totalTrans);
        m.put("avgLatencyMs", avgLatency);
        return ResponseEntity.ok(m);
    }

    @GetMapping("/global/stats")
    public ResponseEntity<Map<String, Object>> globalStats() {
        Map<String, Object> m = new HashMap<>();
        m.put("inputTokens", metricsService.getInputTokens());
        m.put("outputTokens", metricsService.getOutputTokens());
        return ResponseEntity.ok(m);
    }

    @PostMapping("/admin/shutdown")
    public ResponseEntity<Map<String, Object>> shutdown() {
        if (metricsService.isShutdownRequested()) {
            Map<String, Object> m = new HashMap<>();
            m.put("timestamp", Instant.now().toString());
            m.put("status", 409);
            m.put("error", "Conflict");
            m.put("message", "Graceful shutdown is already in progress.");
            m.put("path", "/api/v1/admin/shutdown");
            return ResponseEntity.status(409).body(m);
        }

        metricsService.requestShutdown();
        Map<String, Object> m = new HashMap<>();
        m.put("message", "Graceful shutdown requested.");

        new Thread(() -> {
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            SpringApplication.exit(ctx, () -> 0);
        }).start();

        return ResponseEntity.accepted().body(m);
    }
}
