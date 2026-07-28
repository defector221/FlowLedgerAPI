package com.flowledger.demo.job;

import com.flowledger.demo.DemoSeedResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DemoSeedJob implements DemoSeedProgressSink {
    public enum Status {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        SKIPPED
    }

    private final UUID id = UUID.randomUUID();
    private final String scenario;
    private final String organizationName;
    private final Instant createdAt = Instant.now();
    private final AtomicReference<Status> status = new AtomicReference<>(Status.QUEUED);
    private final AtomicReference<String> stage = new AtomicReference<>("Queued");
    private final AtomicReference<String> label = new AtomicReference<>("");
    private final AtomicInteger current = new AtomicInteger(0);
    private final AtomicInteger total = new AtomicInteger(0);
    private final AtomicInteger percent = new AtomicInteger(0);
    private final AtomicInteger stageIndex = new AtomicInteger(0);
    private final int totalStages;
    private final AtomicReference<String> message = new AtomicReference<>("Waiting to start");
    private final AtomicReference<String> error = new AtomicReference<>(null);
    private final AtomicReference<DemoSeedResult> result = new AtomicReference<>(null);
    private final AtomicReference<Instant> startedAt = new AtomicReference<>(null);
    private final AtomicReference<Instant> finishedAt = new AtomicReference<>(null);

    public DemoSeedJob(String scenario, String organizationName, int totalStages) {
        this.scenario = scenario;
        this.organizationName = organizationName;
        this.totalStages = Math.max(1, totalStages);
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public String getScenario() {
        return scenario;
    }

    public void markRunning() {
        status.set(Status.RUNNING);
        startedAt.compareAndSet(null, Instant.now());
        message.set("Running");
    }

    public void markCompleted(DemoSeedResult seedResult) {
        result.set(seedResult);
        if (seedResult != null && "SKIPPED".equalsIgnoreCase(seedResult.status())) {
            status.set(Status.SKIPPED);
            message.set(seedResult.message());
        } else {
            status.set(Status.COMPLETED);
            message.set(seedResult != null ? seedResult.message() : "Completed");
        }
        percent.set(100);
        finishedAt.set(Instant.now());
    }

    public void markFailed(String err) {
        status.set(Status.FAILED);
        error.set(err);
        message.set(err);
        finishedAt.set(Instant.now());
    }

    @Override
    public void onStage(String stageName) {
        stage.set(stageName);
        label.set("");
        current.set(0);
        total.set(0);
        int idx = stageIndex.updateAndGet(v -> Math.min(v + 1, totalStages));
        percent.set(Math.min(99, (int) Math.round(idx * 100.0 / totalStages)));
        message.set(stageName);
    }

    @Override
    public void onProgress(String progressLabel, int cur, int tot) {
        label.set(progressLabel);
        current.set(cur);
        total.set(tot);
        int idx = Math.max(1, Math.min(stageIndex.get(), totalStages));
        double stageBase = (idx - 1) * 100.0 / totalStages;
        double within = tot > 0 ? (cur * 100.0 / tot) / totalStages : 0;
        percent.set(Math.min(99, (int) Math.round(stageBase + within)));
        message.set(progressLabel + " " + cur + "/" + tot);
    }

    @Override
    public void onDone(String stageName) {
        message.set(stageName);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("scenario", scenario);
        m.put("organizationName", organizationName);
        m.put("status", status.get().name());
        m.put("stage", stage.get());
        m.put("label", label.get());
        m.put("current", current.get());
        m.put("total", total.get());
        m.put("percent", percent.get());
        m.put("stageIndex", stageIndex.get());
        m.put("totalStages", totalStages);
        m.put("message", message.get());
        m.put("error", error.get());
        m.put("createdAt", createdAt);
        m.put("startedAt", startedAt.get());
        m.put("finishedAt", finishedAt.get());
        DemoSeedResult r = result.get();
        if (r != null) {
            m.put("result", r);
        }
        return m;
    }
}
