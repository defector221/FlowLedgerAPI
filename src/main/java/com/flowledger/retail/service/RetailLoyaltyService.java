package com.flowledger.retail.service;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.retail.domain.RetailEnums.GiftCardStatus;
import com.flowledger.retail.entity.RetailGiftCard;
import com.flowledger.retail.entity.RetailLoyaltyAccount;
import com.flowledger.retail.entity.RetailLoyaltyTier;
import com.flowledger.retail.entity.RetailLoyaltyTransaction;
import com.flowledger.retail.repository.RetailGiftCardRepository;
import com.flowledger.retail.repository.RetailLoyaltyAccountRepository;
import com.flowledger.retail.repository.RetailLoyaltyTierRepository;
import com.flowledger.retail.repository.RetailLoyaltyTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RetailLoyaltyService {
    private final RetailModuleGuard guard;
    private final RetailLoyaltyTierRepository tiers;
    private final RetailLoyaltyAccountRepository accounts;
    private final RetailLoyaltyTransactionRepository transactions;
    private final RetailGiftCardRepository giftCards;

    public RetailLoyaltyService(
            RetailModuleGuard guard,
            RetailLoyaltyTierRepository tiers,
            RetailLoyaltyAccountRepository accounts,
            RetailLoyaltyTransactionRepository transactions,
            RetailGiftCardRepository giftCards) {
        this.guard = guard;
        this.tiers = tiers;
        this.accounts = accounts;
        this.transactions = transactions;
        this.giftCards = giftCards;
    }

    // ------------------------------------------------------------------- Tiers
    @Transactional(readOnly = true)
    public List<TierResponse> listTiers() {
        return tiers.findByOrganizationIdAndDeletedFalseOrderByMinPointsAsc(org()).stream()
                .map(this::map)
                .toList();
    }

    public TierResponse createTier(TierRequest r) {
        String code = code(r.code());
        if (tiers.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), code)) {
            conflict("Tier code already exists");
        }
        RetailLoyaltyTier e = new RetailLoyaltyTier();
        e.setOrganizationId(org());
        e.setCode(code);
        e.setName(r.name());
        e.setMinPoints(r.minPoints() == null ? BigDecimal.ZERO : r.minPoints());
        e.setEarnRate(r.earnRate() == null ? BigDecimal.ONE : r.earnRate());
        audit(e, true);
        return map(tiers.save(e));
    }

    public TierResponse updateTier(UUID id, TierRequest r) {
        RetailLoyaltyTier e =
                tiers.findByIdAndOrganizationIdAndDeletedFalse(id, org()).orElseThrow(() -> notFound("Tier not found"));
        e.setName(r.name());
        if (r.minPoints() != null) {
            e.setMinPoints(r.minPoints());
        }
        if (r.earnRate() != null) {
            e.setEarnRate(r.earnRate());
        }
        audit(e, false);
        return map(tiers.save(e));
    }

    public void deleteTier(UUID id) {
        RetailLoyaltyTier e =
                tiers.findByIdAndOrganizationIdAndDeletedFalse(id, org()).orElseThrow(() -> notFound("Tier not found"));
        e.setDeleted(true);
        audit(e, false);
    }

    // --------------------------------------------------------- Loyalty accounts
    @Transactional(readOnly = true)
    public LoyaltyAccountResponse getAccount(UUID customerId) {
        return map(loadOrCreateAccount(customerId, null));
    }

    public LoyaltyAccountResponse getOrCreateAccount(LoyaltyAccountRequest r) {
        return map(loadOrCreateAccount(r.customerId(), r.tierId()));
    }

    public LoyaltyTransactionResponse earn(EarnRequest r) {
        if (r.points() == null || r.points().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points must be positive");
        }
        RetailLoyaltyAccount account = loadOrCreateAccount(r.customerId(), null);
        account.setPointsBalance(account.getPointsBalance().add(r.points()));
        account.setLifetimePoints(account.getLifetimePoints().add(r.points()));
        audit(account, false);
        accounts.save(account);
        return saveTxn(account.getId(), "EARN", r.points(), r.referenceType(), r.referenceId(), r.notes());
    }

    public LoyaltyTransactionResponse redeem(RedeemRequest r) {
        if (r.points() == null || r.points().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points must be positive");
        }
        RetailLoyaltyAccount account = accounts.findByOrganizationIdAndCustomerId(org(), r.customerId())
                .orElseThrow(() -> notFound("Loyalty account not found"));
        if (account.getPointsBalance().compareTo(r.points()) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient loyalty points");
        }
        account.setPointsBalance(account.getPointsBalance().subtract(r.points()));
        audit(account, false);
        accounts.save(account);
        return saveTxn(account.getId(), "REDEEM", r.points(), r.referenceType(), r.referenceId(), r.notes());
    }

    private RetailLoyaltyAccount loadOrCreateAccount(UUID customerId, UUID tierId) {
        return accounts.findByOrganizationIdAndCustomerId(org(), customerId)
                .map(existing -> {
                    if (tierId != null && !tierId.equals(existing.getTierId())) {
                        tiers.findByIdAndOrganizationIdAndDeletedFalse(tierId, org())
                                .orElseThrow(() -> notFound("Tier not found"));
                        existing.setTierId(tierId);
                        audit(existing, false);
                        return accounts.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    if (tierId != null) {
                        tiers.findByIdAndOrganizationIdAndDeletedFalse(tierId, org())
                                .orElseThrow(() -> notFound("Tier not found"));
                    }
                    RetailLoyaltyAccount e = new RetailLoyaltyAccount();
                    e.setOrganizationId(org());
                    e.setCustomerId(customerId);
                    e.setTierId(tierId);
                    e.setPointsBalance(BigDecimal.ZERO);
                    e.setLifetimePoints(BigDecimal.ZERO);
                    audit(e, true);
                    return accounts.save(e);
                });
    }

    private LoyaltyTransactionResponse saveTxn(
            UUID accountId, String type, BigDecimal points, String refType, UUID refId, String notes) {
        RetailLoyaltyTransaction txn = new RetailLoyaltyTransaction();
        txn.setOrganizationId(org());
        txn.setAccountId(accountId);
        txn.setTxnType(type);
        txn.setPoints(points);
        txn.setReferenceType(refType);
        txn.setReferenceId(refId);
        txn.setNotes(notes);
        TenantContext.userId().ifPresent(txn::setCreatedBy);
        txn = transactions.save(txn);
        return new LoyaltyTransactionResponse(
                txn.getId(),
                txn.getAccountId(),
                txn.getTxnType(),
                txn.getPoints(),
                txn.getReferenceType(),
                txn.getReferenceId());
    }

    // --------------------------------------------------------------- Gift cards
    public GiftCardResponse issue(GiftCardIssueRequest r) {
        if (giftCards.existsByOrganizationIdAndCardNumberAndDeletedFalse(org(), r.cardNumber())) {
            conflict("Gift card number already exists");
        }
        RetailGiftCard e = new RetailGiftCard();
        e.setOrganizationId(org());
        e.setCardNumber(r.cardNumber());
        e.setStatus(GiftCardStatus.ISSUED);
        e.setInitialBalance(r.initialBalance());
        e.setBalance(r.initialBalance());
        e.setCustomerId(r.customerId());
        e.setExpiresAt(r.expiresAt());
        audit(e, true);
        return map(giftCards.save(e));
    }

    public GiftCardResponse activate(UUID id) {
        RetailGiftCard e = loadGiftCard(id);
        if (e.getStatus() != GiftCardStatus.ISSUED && e.getStatus() != GiftCardStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Gift card cannot be activated from " + e.getStatus());
        }
        ensureNotExpired(e);
        e.setStatus(GiftCardStatus.ACTIVE);
        if (e.getActivatedAt() == null) {
            e.setActivatedAt(OffsetDateTime.now());
        }
        audit(e, false);
        return map(giftCards.save(e));
    }

    public GiftCardResponse redeem(UUID id, GiftCardAmountRequest r) {
        RetailGiftCard e = loadGiftCard(id);
        ensureActive(e);
        if (e.getBalance().compareTo(r.amount()) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient gift card balance");
        }
        e.setBalance(e.getBalance().subtract(r.amount()));
        if (e.getBalance().signum() == 0) {
            e.setStatus(GiftCardStatus.REDEEMED);
        }
        audit(e, false);
        return map(giftCards.save(e));
    }

    public GiftCardResponse reload(UUID id, GiftCardAmountRequest r) {
        RetailGiftCard e = loadGiftCard(id);
        if (e.getStatus() == GiftCardStatus.CANCELLED || e.getStatus() == GiftCardStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot reload " + e.getStatus() + " gift card");
        }
        ensureNotExpired(e);
        e.setBalance(e.getBalance().add(r.amount()));
        if (e.getStatus() == GiftCardStatus.REDEEMED || e.getStatus() == GiftCardStatus.ISSUED) {
            e.setStatus(GiftCardStatus.ACTIVE);
            if (e.getActivatedAt() == null) {
                e.setActivatedAt(OffsetDateTime.now());
            }
        }
        audit(e, false);
        return map(giftCards.save(e));
    }

    @Transactional(readOnly = true)
    public GiftCardBalanceResponse balance(String cardNumber) {
        RetailGiftCard e = giftCards
                .findByOrganizationIdAndCardNumberAndDeletedFalse(org(), cardNumber)
                .orElseThrow(() -> notFound("Gift card not found"));
        return new GiftCardBalanceResponse(e.getCardNumber(), e.getStatus(), e.getBalance());
    }

    @Transactional(readOnly = true)
    public GiftCardResponse getGiftCard(UUID id) {
        return map(loadGiftCard(id));
    }

    private void ensureActive(RetailGiftCard e) {
        ensureNotExpired(e);
        if (e.getStatus() != GiftCardStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Gift card is not active");
        }
    }

    private void ensureNotExpired(RetailGiftCard e) {
        if (e.getExpiresAt() != null && e.getExpiresAt().isBefore(LocalDate.now())) {
            e.setStatus(GiftCardStatus.EXPIRED);
            audit(e, false);
            giftCards.save(e);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Gift card has expired");
        }
    }

    private RetailGiftCard loadGiftCard(UUID id) {
        return giftCards
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Gift card not found"));
    }

    private TierResponse map(RetailLoyaltyTier e) {
        return new TierResponse(e.getId(), e.getCode(), e.getName(), e.getMinPoints(), e.getEarnRate(), e.getVersion());
    }

    private LoyaltyAccountResponse map(RetailLoyaltyAccount e) {
        return new LoyaltyAccountResponse(
                e.getId(), e.getCustomerId(), e.getTierId(), e.getPointsBalance(), e.getLifetimePoints());
    }

    private GiftCardResponse map(RetailGiftCard e) {
        return new GiftCardResponse(
                e.getId(),
                e.getCardNumber(),
                e.getStatus(),
                e.getInitialBalance(),
                e.getBalance(),
                e.getCustomerId(),
                e.getExpiresAt(),
                e.getActivatedAt(),
                e.getVersion());
    }

    private UUID org() {
        return guard.ensureEnabled();
    }

    private String code(String provided) {
        return provided.trim().toUpperCase(Locale.ROOT);
    }

    private void audit(com.flowledger.common.entity.AuditedEntity e, boolean created) {
        TenantContext.userId().ifPresent(u -> {
            if (created) {
                e.setCreatedBy(u);
            }
            e.setUpdatedBy(u);
        });
    }

    private ResponseStatusException notFound(String m) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, m);
    }

    private void conflict(String m) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, m);
    }
}
