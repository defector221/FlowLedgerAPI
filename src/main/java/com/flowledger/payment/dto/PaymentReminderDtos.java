package com.flowledger.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public final class PaymentReminderDtos {
    private PaymentReminderDtos() {}

    public record SendReminderRequest(List<String> channels) {}

    public record SendReminderResponse(UUID reminderId, boolean sent, String message) {}

    public record ReminderRuleRequest(
            @NotBlank String name,
            @NotNull Integer daysOffset,
            @NotBlank String offsetType,
            @NotBlank String channel,
            Boolean enabled,
            String subjectTemplate,
            String bodyTemplate) {}

    public record ReminderRuleResponse(
            UUID id,
            String name,
            int daysOffset,
            String offsetType,
            String channel,
            boolean enabled,
            String subjectTemplate,
            String bodyTemplate) {}
}
