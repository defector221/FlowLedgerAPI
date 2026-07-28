package com.flowledger.demo.util;

import org.slf4j.Logger;

public final class ProgressLogger {
    private final Logger log;
    private final String scenario;

    public ProgressLogger(Logger log, String scenario) {
        this.log = log;
        this.scenario = scenario;
    }

    public void stage(String stage) {
        log.info("[{}] ▶ {}", scenario, stage);
    }

    public void progress(String label, int current, int total) {
        if (total <= 0) return;
        if (current == total || current % Math.max(1, total / 10) == 0) {
            int pct = (int) Math.round(current * 100.0 / total);
            log.info("[{}] {} {}/{} ({}%)", scenario, label, current, total, pct);
        }
    }

    public void done(String stage) {
        log.info("[{}] ✓ {}", scenario, stage);
    }
}
