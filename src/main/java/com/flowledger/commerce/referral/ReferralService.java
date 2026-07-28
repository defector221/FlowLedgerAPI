package com.flowledger.commerce.referral;

import com.flowledger.commerce.referral.entity.CommerceReferralCode;
import com.flowledger.commerce.referral.entity.CommerceReferralEvent;
import com.flowledger.commerce.referral.repository.CommerceReferralCodeRepository;
import com.flowledger.commerce.referral.repository.CommerceReferralEventRepository;
import com.flowledger.commerce.wallet.WalletLedgerService;
import com.flowledger.commerce.wallet.domain.WalletAccountType;
import com.flowledger.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReferralService {
    private static final BigDecimal REFERRER_REWARD = new BigDecimal("100");
    private static final BigDecimal REFEREE_REWARD = new BigDecimal("50");

    private final CommerceReferralCodeRepository codes;
    private final CommerceReferralEventRepository events;
    private final WalletLedgerService wallet;

    public ReferralService(
            CommerceReferralCodeRepository codes,
            CommerceReferralEventRepository events,
            WalletLedgerService wallet) {
        this.codes = codes;
        this.events = events;
        this.wallet = wallet;
    }

    public CommerceReferralCode getOrCreateCode(UUID customerId, UUID organizationId) {
        return codes.findByCustomerIdAndOrganizationId(customerId, organizationId)
                .orElseGet(() -> {
                    CommerceReferralCode code = new CommerceReferralCode();
                    code.setCustomerId(customerId);
                    code.setOrganizationId(organizationId);
                    code.setCode(generateCode(customerId));
                    return codes.save(code);
                });
    }

    public CommerceReferralEvent registerReferee(UUID organizationId, UUID refereeCustomerId, String referralCode) {
        CommerceReferralCode code = codes.findByCodeIgnoreCase(referralCode.trim())
                .orElseThrow(() -> new BusinessException("Invalid referral code"));
        if (code.getCustomerId().equals(refereeCustomerId)) {
            throw new BusinessException("Cannot refer yourself");
        }
        return events.findByRefereeCustomerIdAndOrganizationId(refereeCustomerId, organizationId)
                .orElseGet(() -> {
                    CommerceReferralEvent event = new CommerceReferralEvent();
                    event.setOrganizationId(organizationId);
                    event.setReferrerCustomerId(code.getCustomerId());
                    event.setRefereeCustomerId(refereeCustomerId);
                    event.setStatus("REGISTERED");
                    return events.save(event);
                });
    }

    public void rewardOnFirstOrder(UUID organizationId, UUID refereeCustomerId, UUID orderId) {
        CommerceReferralEvent event = events
                .findByRefereeCustomerIdAndOrganizationId(refereeCustomerId, organizationId)
                .orElse(null);
        if (event == null || !"REGISTERED".equals(event.getStatus())) return;

        wallet.credit(
                event.getReferrerCustomerId(),
                organizationId,
                WalletAccountType.PROMOTIONAL,
                REFERRER_REWARD,
                "ReferralReward",
                orderId,
                "Referrer reward");
        wallet.credit(
                refereeCustomerId,
                organizationId,
                WalletAccountType.PROMOTIONAL,
                REFEREE_REWARD,
                "ReferralReward",
                orderId,
                "Referee reward");

        event.setOrderId(orderId);
        event.setStatus("REWARDED");
        event.setRewardedAt(OffsetDateTime.now());
        events.save(event);
    }

    private static String generateCode(UUID customerId) {
        return ("REF" + customerId.toString().replace("-", "").substring(0, 8)).toUpperCase(Locale.ROOT);
    }
}
