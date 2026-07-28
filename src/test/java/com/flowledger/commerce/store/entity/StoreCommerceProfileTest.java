package com.flowledger.commerce.store.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.flowledger.commerce.fulfillment.FulfillmentType;
import com.flowledger.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class StoreCommerceProfileTest {
    @Test
    void commerceEnablementIndependentFromPublication() {
        StoreCommerceProfile profile = new StoreCommerceProfile();
        profile.enableCommerce();
        profile.setSupportsClickCollect(true);
        assertDoesNotThrow(() -> profile.assertCanAcceptDigitalOrder(FulfillmentType.CLICK_AND_COLLECT));
        assertThrows(BusinessException.class, () -> profile.assertCanPublishProducts());
    }

    @Test
    void publicationRequiresFlags() {
        StoreCommerceProfile profile = new StoreCommerceProfile();
        profile.publishStoreToMarketplace();
        profile.setPublishProducts(true);
        assertDoesNotThrow(profile::assertCanPublishProducts);
    }
}
