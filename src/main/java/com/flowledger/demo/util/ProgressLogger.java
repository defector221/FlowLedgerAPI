package com.flowledger.demo.util;

import com.flowledger.demo.job.DemoSeedProgressSink;
import org.slf4j.Logger;

public final class ProgressLogger {
    private final Logger log;
    private final String scenario;
    private final DemoSeedProgressSink sink;
    private final int totalStages;
    private int stageIndex;

    public static final int DEFAULT_PIPELINE_STAGES = 13;

    public ProgressLogger(Logger log, String scenario) {
        this(log, scenario, null, DEFAULT_PIPELINE_STAGES);
    }

    public ProgressLogger(Logger log, String scenario, DemoSeedProgressSink sink, int totalStages) {
        this.log = log;
        this.scenario = scenario;
        this.sink = sink;
        this.totalStages = Math.max(1, totalStages);
    }

    public void stage(String stage) {
        stageIndex = Math.min(stageIndex + 1, totalStages);
        log.info("[{}] ▶ {}", scenario, stage);
        if (sink != null) {
            sink.onStage(stage);
        }
    }

    public void progress(String label, int current, int total) {
        if (total <= 0) return;
        if (current == total || current % Math.max(1, total / 10) == 0) {
            int pct = (int) Math.round(current * 100.0 / total);
            log.info("[{}] {} {}/{} ({}%)", scenario, label, current, total, pct);
        }
        if (sink != null) {
            sink.onProgress(label, current, total);
        }
    }

    public void done(String stage) {
        log.info("[{}] ✓ {}", scenario, stage);
        if (sink != null) {
            sink.onDone(stage);
        }
    }

    public int stageIndex() {
        return stageIndex;
    }

    public int totalStages() {
        return totalStages;
    }
}
