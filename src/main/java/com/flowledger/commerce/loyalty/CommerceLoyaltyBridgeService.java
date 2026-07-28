package com.flowledger.commerce.loyalty;

import com.flowledger.commerce.common.CommerceTenantScope;
import com.flowledger.commerce.customer.bridge.CommerceCustomerBridgeService;
import com.flowledger.retail.dto.RetailDtos.EarnRequest;
import com.flowledger.retail.dto.RetailDtos.LoyaltyAccountResponse;
import com.flowledger.retail.dto.RetailDtos.RedeemRequest;
import com.flowledger.retail.service.RetailLoyaltyService;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommerceLoyaltyBridgeService {
    private final CommerceCustomerBridgeService customerBridge;
    private final RetailLoyaltyService retailLoyalty;

    public CommerceLoyaltyBridgeService(
            CommerceCustomerBridgeService customerBridge, RetailLoyaltyService retailLoyalty) {
        this.customerBridge = customerBridge;
        this.retailLoyalty = retailLoyalty;
    }

    @Transactional(readOnly = true)
    public LoyaltyAccountResponse getAccount(UUID organizationId, UUID commerceCustomerId) {
        UUID erpCustomerId = customerBridge.findOrCreateErpCustomer(organizationId, commerceCustomerId);
        return CommerceTenantScope.run(organizationId, () -> retailLoyalty.getAccount(erpCustomerId));
    }

    public void earn(UUID organizationId, UUID commerceCustomerId, BigDecimal points, String referenceType, UUID referenceId) {
        UUID erpCustomerId = customerBridge.findOrCreateErpCustomer(organizationId, commerceCustomerId);
        CommerceTenantScope.run(organizationId, () -> retailLoyalty.earn(new EarnRequest(
                erpCustomerId, points, referenceType, referenceId, "Commerce loyalty earn")));
    }

    public void redeem(UUID organizationId, UUID commerceCustomerId, BigDecimal points, String referenceType, UUID referenceId) {
        UUID erpCustomerId = customerBridge.findOrCreateErpCustomer(organizationId, commerceCustomerId);
        CommerceTenantScope.run(organizationId, () -> retailLoyalty.redeem(new RedeemRequest(
                erpCustomerId, points, referenceType, referenceId, "Commerce loyalty redeem")));
    }
}
