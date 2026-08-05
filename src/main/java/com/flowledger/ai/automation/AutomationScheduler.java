package com.flowledger.ai.automation;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAiEnabled
public class AutomationScheduler {
    private static final Logger log = LoggerFactory.getLogger(AutomationScheduler.class);
    private final AutomationService automations;

    public AutomationScheduler(AutomationService automations) {
        this.automations = automations;
    }

    @Scheduled(fixedDelayString = "${flowledger.ai.automation-poll-ms:60000}")
    public void pollDueCrons() {
        try {
            int ran = automations.runDueCrons(OffsetDateTime.now());
            if (ran > 0) {
                log.info("AI automation scheduler completed {} run(s)", ran);
            }
        } catch (Exception e) {
            log.warn("AI automation scheduler failed: {}", e.getMessage());
        }
    }
}
