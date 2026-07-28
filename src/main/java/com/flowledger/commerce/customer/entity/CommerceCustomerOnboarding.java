package com.flowledger.commerce.customer.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.onboarding.domain.CustomerOnboardingState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "commerce_customer_onboarding")
@Getter
@Setter
@NoArgsConstructor
public class CommerceCustomerOnboarding extends CommerceGlobalEntity {
    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerOnboardingState state = CustomerOnboardingState.REGISTERED;

    @Column(name = "state_changed_at", nullable = false)
    private OffsetDateTime stateChangedAt = OffsetDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "history_json", columnDefinition = "jsonb")
    private String historyJson;

    public void advanceTo(CustomerOnboardingState next) {
        this.state = next;
        this.stateChangedAt = OffsetDateTime.now();
    }
}
