package com.flowledger.subscription.dto;

import com.flowledger.subscription.domain.BillingCycle;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class SubscriptionDtos {
    private SubscriptionDtos() {}

    public record PlanResponse(
            UUID id,
            String code,
            String name,
            String description,
            int maxOrganizations,
            int maxUsersPerOrg,
            int maxInvoicesPerMonth,
            BigDecimal priceMonthly,
            BigDecimal priceYearly,
            String currency,
            int displayOrder,
            boolean highlightPlan,
            boolean recommended,
            int trialDays) {}

    public record CheckoutRequest(String planCode, BillingCycle billingCycle) {}

    public record VerifyPaymentRequest(
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature,
            String orderId,
            String paymentId,
            String signature,
            String provider) {
        public String resolvedOrderId() {
            return firstNonBlank(orderId, razorpayOrderId);
        }

        public String resolvedPaymentId() {
            return firstNonBlank(paymentId, razorpayPaymentId);
        }

        public String resolvedSignature() {
            return firstNonBlank(signature, razorpaySignature);
        }

        private static String firstNonBlank(String... values) {
            if (values == null) {
                return null;
            }
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }
    }

    public record CurrentSubscriptionResponse(
            PlanResponse plan,
            String status,
            String billingCycle,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            OffsetDateTime nextBillingDate,
            boolean autoRenew,
            String paymentProvider,
            String paymentReference) {}

    public record UsageResponse(
            int organizationCount,
            int organizationLimit,
            int userCount,
            int userLimit,
            int invoiceCount,
            int invoiceLimit) {}

    public record InvoiceResponse(
            UUID id,
            String invoiceNumber,
            BigDecimal amount,
            BigDecimal gst,
            BigDecimal discount,
            BigDecimal total,
            OffsetDateTime paidAt,
            String pdfUrl,
            OffsetDateTime createdAt) {}

    public record CheckoutResponse(
            boolean activated,
            String provider,
            String keyId,
            String orderId,
            BigDecimal amount,
            String currency,
            String planCode,
            String billingCycle,
            UUID paymentTransactionId,
            CurrentSubscriptionResponse subscription,
            String clientSecret,
            String checkoutUrl) {}
}
