package com.flowledger.demo.job;

/** Callback for demo seed progress (UI polling / job store). */
public interface DemoSeedProgressSink {
    void onStage(String stage);

    void onProgress(String label, int current, int total);

    void onDone(String stage);
}
