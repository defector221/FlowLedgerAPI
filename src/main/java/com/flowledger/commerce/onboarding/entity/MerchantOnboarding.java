package com.flowledger.commerce.onboarding.entity;

import com.flowledger.commerce.onboarding.domain.MerchantOnboardingState;
import com.flowledger.common.entity.AuditedEntity;
import com.flowledger.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "merchant_onboarding")
@Getter
@Setter
@NoArgsConstructor
public class MerchantOnboarding extends AuditedEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantOnboardingState state = MerchantOnboardingState.REGISTERED;

    @Column(name = "state_changed_at", nullable = false)
    private OffsetDateTime stateChangedAt = OffsetDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "history_json", columnDefinition = "jsonb")
    private String historyJson;

    public void advanceTo(MerchantOnboardingState next) {
        if (state == MerchantOnboardingState.LIVE && next != MerchantOnboardingState.SUSPENDED) {
            throw new BusinessException("Cannot change state after LIVE except to SUSPENDED");
        }
        this.state = next;
        this.stateChangedAt = OffsetDateTime.now();
    }

    public boolean isLive() {
        return state == MerchantOnboardingState.LIVE;
    }
}
