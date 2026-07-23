package com.flowledger.ai.controller;

import com.flowledger.ai.config.ConditionalOnAiDisabled;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Keeps {@code /api/v1/ai/**} from falling through to the static resource handler when AI is
 * disabled, and returns a clear 503 instead of a misleading 500.
 */
@RestController
@RequestMapping("/api/v1/ai")
@ConditionalOnAiDisabled
public class AiDisabledController {

    @RequestMapping(
            value = {"", "/", "/**"},
            method = {
                RequestMethod.GET,
                RequestMethod.POST,
                RequestMethod.PUT,
                RequestMethod.PATCH,
                RequestMethod.DELETE
            })
    public void disabled() {
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI module is disabled. Set FLOWLEDGER_AI_ENABLED=true and restart the API.");
    }
}
