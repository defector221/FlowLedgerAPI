package com.flowledger.commerce.fulfillment.verification;

import com.flowledger.commerce.fulfillment.pickup.entity.CollectToken;
import com.flowledger.commerce.fulfillment.pickup.repository.CollectTokenRepository;
import com.flowledger.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QrTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CollectTokenRepository collectTokens;

    public QrTokenService(CollectTokenRepository collectTokens) {
        this.collectTokens = collectTokens;
    }

    /** Returns an active 6-digit collect code, creating one if needed. */
    public String getOrIssueCollectCode(UUID fulfillmentOrderId) {
        return collectTokens
                .findFirstByFulfillmentOrderIdAndVerifiedAtIsNullOrderByCreatedAtDesc(fulfillmentOrderId)
                .filter(token -> token.getExpiresAt().isAfter(OffsetDateTime.now()))
                .map(CollectToken::getCollectCode)
                .filter(code -> code != null && !code.isBlank())
                .orElseGet(() -> issueCollectCode(fulfillmentOrderId));
    }

    /** @deprecated use {@link #getOrIssueCollectCode(UUID)} */
    public String issueCollectToken(UUID fulfillmentOrderId) {
        return getOrIssueCollectCode(fulfillmentOrderId);
    }

    public UUID verifyCollectToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException("Collect code is required");
        }
        String normalized = rawToken.trim();
        CollectToken token;
        if (normalized.matches("\\d{6}")) {
            token = collectTokens
                    .findByCollectCodeAndVerifiedAtIsNull(normalized)
                    .orElseThrow(() -> new BusinessException("Invalid collect code"));
        } else {
            token = collectTokens
                    .findByTokenHash(hash(normalized))
                    .orElseThrow(() -> new BusinessException("Invalid collect token"));
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException("Collect code expired");
        }
        if (token.getVerifiedAt() != null) {
            throw new BusinessException("Collect code already used");
        }
        token.setVerifiedAt(OffsetDateTime.now());
        collectTokens.save(token);
        return token.getFulfillmentOrderId();
    }

    public String issueExitToken(UUID sessionId) {
        return UUID.randomUUID() + ":exit:" + sessionId;
    }

    public UUID verifyExitToken(String rawToken, String expectedPrefix) {
        if (!rawToken.contains(":exit:")) {
            throw new BusinessException("Invalid exit token");
        }
        return UUID.fromString(rawToken.substring(rawToken.lastIndexOf(':') + 1));
    }

    private String issueCollectCode(UUID fulfillmentOrderId) {
        for (int attempt = 0; attempt < 25; attempt++) {
            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            if (collectTokens.findByCollectCodeAndVerifiedAtIsNull(code).isPresent()) {
                continue;
            }
            CollectToken token = new CollectToken();
            token.setFulfillmentOrderId(fulfillmentOrderId);
            token.setCollectCode(code);
            token.setTokenHash(hash(code + ":" + fulfillmentOrderId));
            token.setExpiresAt(OffsetDateTime.now().plusHours(24));
            collectTokens.save(token);
            return code;
        }
        throw new BusinessException("Could not generate collect code, please try again");
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
