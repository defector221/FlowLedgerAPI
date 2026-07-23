package com.flowledger.transport.service;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TransportModuleAspect {
    private final TransportModuleGuard guard;

    public TransportModuleAspect(TransportModuleGuard guard) {
        this.guard = guard;
    }

    @Before(
            "within(com.flowledger.transport.controller..*) && !within(com.flowledger.transport.controller.TransportWebhookController)")
    public void requireTransportModule() {
        guard.ensureEnabled();
    }
}
