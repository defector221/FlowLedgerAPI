package com.flowledger.commerce.customer.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.customer.domain.CommerceCustomerStatus;
import com.flowledger.commerce.onboarding.domain.CustomerOnboardingState;
import com.flowledger.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_customers")
@Getter
@Setter
@NoArgsConstructor
public class CommerceCustomer extends CommerceGlobalEntity {
    @Column(nullable = false, unique = true)
    private String mobile;

    private String email;
    private String firstName;
    private String lastName;
    private String displayName;

    @Column(name = "profile_photo")
    private String profilePhoto;

  private LocalDate dateOfBirth;
    private String gender;

    @Column(name = "preferred_language")
    private String preferredLanguage = "en";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommerceCustomerStatus status = CommerceCustomerStatus.REGISTERED;

    @Column(name = "marketing_consent", nullable = false)
    private boolean marketingConsent;

    @Column(name = "notification_consent", nullable = false)
    private boolean notificationConsent = true;

    public void requestOtp() {
        this.status = CommerceCustomerStatus.OTP_PENDING;
    }

    public void activate() {
        this.status = CommerceCustomerStatus.ACTIVE;
    }

    public void block() {
        this.status = CommerceCustomerStatus.BLOCKED;
    }

    public void completeProfile(String firstName, String lastName, String displayName) {
        if (firstName != null) this.firstName = firstName;
        if (lastName != null) this.lastName = lastName;
        if (displayName != null) this.displayName = displayName;
        if (this.status == CommerceCustomerStatus.VERIFIED || this.status == CommerceCustomerStatus.OTP_PENDING) {
            this.status = CommerceCustomerStatus.PROFILE_COMPLETED;
        }
    }

    public void assertActive() {
        if (status == CommerceCustomerStatus.BLOCKED) {
            throw new BusinessException("Customer account is blocked");
        }
        if (status != CommerceCustomerStatus.ACTIVE && status != CommerceCustomerStatus.PROFILE_COMPLETED) {
            throw new BusinessException("Customer account is not active");
        }
    }

    public CustomerOnboardingState onboardingState() {
        return switch (status) {
            case REGISTERED -> CustomerOnboardingState.REGISTERED;
            case OTP_PENDING -> CustomerOnboardingState.OTP_PENDING;
            case VERIFIED -> CustomerOnboardingState.VERIFIED;
            case PROFILE_COMPLETED -> CustomerOnboardingState.PROFILE_COMPLETED;
            case ACTIVE -> CustomerOnboardingState.ACTIVE;
            case BLOCKED -> CustomerOnboardingState.BLOCKED;
        };
    }
}
