package com.flowledger.commerce.fulfillment.verification;

import com.flowledger.commerce.fulfillment.pickup.entity.CollectToken;
import com.flowledger.commerce.fulfillment.pickup.repository.CollectTokenRepository;
import com.flowledger.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QrTokenService {
    private final CollectTokenRepository collectTokens;

    public QrTokenService(CollectTokenRepository collectTokens) {
        this.collectTokens = collectTokens;
    }

    public String issueCollectToken(UUID fulfillmentOrderId) {
        String raw = UUID.randomUUID() + ":" + fulfillmentOrderId;
        String hash = hash(raw);
        CollectToken token = new CollectToken();
        token.setFulfillmentOrderId(fulfillmentOrderId);
        token.setTokenHash(hash);
        token.setExpiresAt(OffsetDateTime.now().plusHours(24));
        collectTokens.save(token);
        return raw;
    }

    public UUID verifyCollectToken(String rawToken) {
        CollectToken token = collectTokens
                .findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BusinessException("Invalid collect token"));
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException("Collect token expired");
        }
        if (token.getVerifiedAt() != null) {
            throw new BusinessException("Collect token already used");
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

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
