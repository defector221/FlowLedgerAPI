package com.flowledger.retail.controller;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.retail.service.RetailLoyaltyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/retail")
public class RetailLoyaltyController {
    private final RetailLoyaltyService service;

    public RetailLoyaltyController(RetailLoyaltyService service) {
        this.service = service;
    }

    // ------------------------------------------------------------------- Tiers
    @GetMapping("/loyalty/tiers")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<TierResponse> listTiers() {
        return service.listTiers();
    }

    @PostMapping("/loyalty/tiers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public TierResponse createTier(@Valid @RequestBody TierRequest r) {
        return service.createTier(r);
    }

    @PutMapping("/loyalty/tiers/{id}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public TierResponse updateTier(@PathVariable UUID id, @Valid @RequestBody TierRequest r) {
        return service.updateTier(id, r);
    }

    @DeleteMapping("/loyalty/tiers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deleteTier(@PathVariable UUID id) {
        service.deleteTier(id);
    }

    // --------------------------------------------------------- Loyalty accounts
    @GetMapping("/loyalty/accounts/{customerId}")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public LoyaltyAccountResponse getAccount(@PathVariable UUID customerId) {
        return service.getAccount(customerId);
    }

    @PostMapping("/loyalty/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public LoyaltyAccountResponse getOrCreateAccount(@Valid @RequestBody LoyaltyAccountRequest r) {
        return service.getOrCreateAccount(r);
    }

    @PostMapping("/loyalty/earn")
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public LoyaltyTransactionResponse earn(@Valid @RequestBody EarnRequest r) {
        return service.earn(r);
    }

    @PostMapping("/loyalty/redeem")
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public LoyaltyTransactionResponse redeem(@Valid @RequestBody RedeemRequest r) {
        return service.redeem(r);
    }

    // --------------------------------------------------------------- Gift cards
    @GetMapping("/gift-cards/{id}")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public GiftCardResponse getGiftCard(@PathVariable UUID id) {
        return service.getGiftCard(id);
    }

    @GetMapping("/gift-cards/balance")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public GiftCardBalanceResponse balance(@RequestParam String cardNumber) {
        return service.balance(cardNumber);
    }

    @PostMapping("/gift-cards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public GiftCardResponse issue(@Valid @RequestBody GiftCardIssueRequest r) {
        return service.issue(r);
    }

    @PostMapping("/gift-cards/{id}/activate")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public GiftCardResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/gift-cards/{id}/redeem")
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public GiftCardResponse redeemGiftCard(
            @PathVariable UUID id, @Valid @RequestBody GiftCardAmountRequest r) {
        return service.redeem(id, r);
    }

    @PostMapping("/gift-cards/{id}/reload")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public GiftCardResponse reload(@PathVariable UUID id, @Valid @RequestBody GiftCardAmountRequest r) {
        return service.reload(id, r);
    }
}
