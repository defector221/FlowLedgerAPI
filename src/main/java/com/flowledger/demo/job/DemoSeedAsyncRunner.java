package com.flowledger.demo.job;

import com.flowledger.demo.DemoSeedRequest;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class DemoSeedAsyncRunner {
    private final DemoSeedJobService jobs;

    public DemoSeedAsyncRunner(@Lazy DemoSeedJobService jobs) {
        this.jobs = jobs;
    }

    @Async
    public void run(UUID jobId, DemoSeedRequest request) {
        jobs.execute(jobId, request);
    }
}
