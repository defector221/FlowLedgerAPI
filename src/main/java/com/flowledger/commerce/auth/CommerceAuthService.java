package com.flowledger.commerce.auth;

import com.flowledger.commerce.auth.entity.CommerceOtpChallenge;
import com.flowledger.commerce.auth.repository.CommerceOtpChallengeRepository;
import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.commerce.customer.domain.CommerceCustomerStatus;
import com.flowledger.commerce.customer.entity.CommerceCustomer;
import com.flowledger.commerce.customer.entity.CommerceCustomerOnboarding;
import com.flowledger.commerce.customer.repository.CommerceCustomerOnboardingRepository;
import com.flowledger.commerce.customer.repository.CommerceCustomerRepository;
import com.flowledger.commerce.dto.CommerceDtos.CommerceTokenResponse;
import com.flowledger.commerce.dto.CommerceDtos.RequestOtpResponse;
import com.flowledger.commerce.events.CustomerActivatedEvent;
import com.flowledger.commerce.onboarding.domain.CustomerOnboardingState;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.platform.event.DomainEventPublisher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommerceAuthService {
    private final CommerceCustomerRepository customers;
    private final CommerceCustomerOnboardingRepository onboardingRepository;
    private final CommerceOtpChallengeRepository otpRepository;
    private final CommerceProperties properties;
    private final CommerceJwtService jwt;
    private final DomainEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public CommerceAuthService(
            CommerceCustomerRepository customers,
            CommerceCustomerOnboardingRepository onboardingRepository,
            CommerceOtpChallengeRepository otpRepository,
            CommerceProperties properties,
            CommerceJwtService jwt,
            DomainEventPublisher events) {
        this.customers = customers;
        this.onboardingRepository = onboardingRepository;
        this.otpRepository = otpRepository;
        this.properties = properties;
        this.jwt = jwt;
        this.events = events;
    }

    public RequestOtpResponse requestOtp(String mobile) {
        String normalized = normalizeMobile(mobile);
        CommerceCustomer customer = customers.findByMobile(normalized).orElseGet(() -> {
            CommerceCustomer created = new CommerceCustomer();
            created.setMobile(normalized);
            CommerceCustomer saved = customers.save(created);
            CommerceCustomerOnboarding onboarding = new CommerceCustomerOnboarding();
            onboarding.setCustomerId(saved.getId());
            onboardingRepository.save(onboarding);
            return saved;
        });
        customer.requestOtp();
        customers.save(customer);

        String otp = properties.getOtp().isDevReturnInResponse()
                ? properties.getOtp().getStubCode()
                : String.format("%06d", random.nextInt(1_000_000));

        CommerceOtpChallenge challenge = new CommerceOtpChallenge();
        challenge.setMobile(normalized);
        challenge.setOtpHash(hash(otp));
        challenge.setExpiresAt(OffsetDateTime.now().plusSeconds(properties.getOtp().getTtlSeconds()));
        otpRepository.save(challenge);

        onboardingRepository.findByCustomerId(customer.getId()).ifPresent(o -> {
            o.advanceTo(CustomerOnboardingState.OTP_PENDING);
            onboardingRepository.save(o);
        });

        return new RequestOtpResponse(
                normalized, true, properties.getOtp().isDevReturnInResponse() ? otp : null);
    }

    public CommerceTokenResponse verifyOtp(String mobile, String otp) {
        String normalized = normalizeMobile(mobile);
        CommerceOtpChallenge challenge = otpRepository
                .findFirstByMobileAndConsumedFalseOrderByCreatedAtDesc(normalized)
                .orElseThrow(() -> new BusinessException("OTP not found or expired"));
        if (challenge.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException("OTP expired");
        }
        if (!challenge.getOtpHash().equals(hash(otp))) {
            throw new BusinessException("Invalid OTP");
        }
        challenge.setConsumed(true);
        otpRepository.save(challenge);

        CommerceCustomer customer = customers
                .findByMobile(normalized)
                .orElseThrow(() -> new BusinessException("Customer not found"));
        customer.setStatus(CommerceCustomerStatus.ACTIVE);
        customers.save(customer);
        onboardingRepository.findByCustomerId(customer.getId()).ifPresent(o -> {
            o.advanceTo(CustomerOnboardingState.ACTIVE);
            onboardingRepository.save(o);
        });
        events.publish(new CustomerActivatedEvent(this, customer.getId()));

        CommercePrincipal principal = new CommercePrincipal(customer.getId(), customer.getMobile());
        return new CommerceTokenResponse(
                jwt.createAccessToken(principal),
                jwt.createRefreshToken(principal),
                customer.getId(),
                customer.getMobile());
    }

    public CommerceTokenResponse refresh(String refreshToken) {
        if (!jwt.isValidRefresh(refreshToken)) {
            throw new BusinessException("Invalid refresh token");
        }
        CommercePrincipal principal = new CommercePrincipal(jwt.customerId(refreshToken), null);
        CommerceCustomer customer = customers
                .findById(principal.getCustomerId())
                .orElseThrow(() -> new BusinessException("Customer not found"));
        principal = new CommercePrincipal(customer.getId(), customer.getMobile());
        return new CommerceTokenResponse(
                jwt.createAccessToken(principal),
                jwt.createRefreshToken(principal),
                customer.getId(),
                customer.getMobile());
    }

    private static String normalizeMobile(String mobile) {
        return mobile.replaceAll("\\s+", "");
    }

    private static String hash(String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(otp.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
