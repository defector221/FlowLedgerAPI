package com.flowledger.demo;

import com.flowledger.demo.config.DemoProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class DemoSeedApplicationRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoSeedApplicationRunner.class);

    private final DemoProperties props;
    private final DemoDataOrchestrator orchestrator;

    public DemoSeedApplicationRunner(DemoProperties props, DemoDataOrchestrator orchestrator) {
        this.props = props;
        this.orchestrator = orchestrator;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.isEnabled() || !props.isSeedOnStartup()) {
            return;
        }
        String scenario = props.getScenario();
        if (args.containsOption("flowledger.demo.scenario")) {
            scenario = args.getOptionValues("flowledger.demo.scenario").get(0);
        }
        // support: seed grocery-chain
        List<String> nonOpts = args.getNonOptionArgs();
        for (int i = 0; i < nonOpts.size() - 1; i++) {
            if ("seed".equalsIgnoreCase(nonOpts.get(i))) {
                scenario = nonOpts.get(i + 1);
            }
        }
        log.info("Demo seed-on-startup running scenario={}", scenario);
        try {
            DemoSeedResult result = orchestrator.seed(new DemoSeedRequest(scenario, "SKIP", null, null));
            log.info(
                    "Demo seed-on-startup finished status={} org={} durationMs={}",
                    result.status(),
                    result.organizationId(),
                    result.durationMs());
        } catch (Exception e) {
            log.error("Demo seed-on-startup failed", e);
        }
    }
}
