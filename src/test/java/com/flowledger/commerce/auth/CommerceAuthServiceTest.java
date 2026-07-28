package com.flowledger.commerce.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.flowledger.commerce.auth.entity.CommerceOtpChallenge;
import com.flowledger.commerce.auth.repository.CommerceOtpChallengeRepository;
import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.commerce.customer.repository.CommerceCustomerOnboardingRepository;
import com.flowledger.commerce.customer.repository.CommerceCustomerRepository;
import com.flowledger.commerce.dto.CommerceDtos.CommerceTokenResponse;
import com.flowledger.platform.event.DomainEventPublisher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommerceAuthServiceTest {
    @Mock
    private CommerceCustomerRepository customers;
    @Mock
    private CommerceCustomerOnboardingRepository onboardingRepository;
    @Mock
    private CommerceOtpChallengeRepository otpRepository;
    @Mock
    private CommerceJwtService jwt;
    @Mock
    private DomainEventPublisher events;

    private CommerceAuthService service;

    @BeforeEach
    void setUp() {
        CommerceProperties properties = new CommerceProperties();
        properties.getOtp().setDevReturnInResponse(true);
        properties.getOtp().setStubCode("000000");
        service = new CommerceAuthService(customers, onboardingRepository, otpRepository, properties, jwt, events);
    }

    @Test
    void verifyOtpIssuesTokens() {
        CommerceOtpChallenge challenge = new CommerceOtpChallenge();
        challenge.setOtpHash(hash("000000"));
        challenge.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        when(otpRepository.findFirstByMobileAndConsumedFalseOrderByCreatedAtDesc("9999999999"))
                .thenReturn(Optional.of(challenge));
        var customer = new com.flowledger.commerce.customer.entity.CommerceCustomer();
        customer.setId(java.util.UUID.randomUUID());
        customer.setMobile("9999999999");
        when(customers.findByMobile("9999999999")).thenReturn(Optional.of(customer));
        when(jwt.createAccessToken(any())).thenReturn("access");
        when(jwt.createRefreshToken(any())).thenReturn("refresh");

        CommerceTokenResponse response = service.verifyOtp("9999999999", "000000");
        assertNotNull(response.accessToken());
        assertEquals("access", response.accessToken());
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
