package au.edu.adelaide.sttassignment1.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Component
public class MetricsService {
    private final Instant start = Instant.now();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalTranscriptions = new AtomicLong(0);
    private final LongAdder totalLatencyMs = new LongAdder();
    private final AtomicLong inputTokens = new AtomicLong(0);
    private final AtomicLong outputTokens = new AtomicLong(0);
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    public Instant getStart() { return start; }
    public void incrementRequests() { totalRequests.incrementAndGet(); }
    public void incrementTranscriptions() { totalTranscriptions.incrementAndGet(); }
    public void addLatency(long ms) { totalLatencyMs.add(ms); }
    public void addInputTokens(long tokens) { inputTokens.addAndGet(tokens); }
    public void addOutputTokens(long tokens) { outputTokens.addAndGet(tokens); }
    public void requestShutdown() { shutdownRequested.set(true); }
    public boolean isShutdownRequested() { return shutdownRequested.get(); }

    public long getTotalRequests() { return totalRequests.get(); }
    public long getTotalTranscriptions() { return totalTranscriptions.get(); }
    public long getTotalLatencyMs() { return totalLatencyMs.sum(); }
    public long getInputTokens() { return inputTokens.get(); }
    public long getOutputTokens() { return outputTokens.get(); }
}
