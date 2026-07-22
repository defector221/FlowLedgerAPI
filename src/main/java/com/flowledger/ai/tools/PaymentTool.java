package com.flowledger.ai.tools;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.payment.entity.Payment;
import com.flowledger.payment.service.PaymentService;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAiEnabled
public class PaymentTool {
    private final PaymentService paymentService;

    public PaymentTool(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Tool("Summarize recent payments")
    public String summarize(String query) {
        List<Payment> payments = paymentService.list();
        String sample = payments.stream()
                .limit(10)
                .map(p -> p.getPaymentNumber()
                        + " date="
                        + p.getPaymentDate()
                        + " type="
                        + p.getPaymentType()
                        + " amount="
                        + p.getAmount()
                        + " status="
                        + p.getStatus())
                .collect(Collectors.joining("; "));
        return "Payments: count="
                + payments.size()
                + ". Recent: "
                + (sample.isBlank() ? "none" : sample)
                + ". Query="
                + query;
    }
}
