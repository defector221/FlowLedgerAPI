package com.flowledger.commerce.mapper;

import com.flowledger.commerce.capability.entity.MerchantCapabilityProfile;
import com.flowledger.commerce.customer.entity.CommerceCustomer;
import com.flowledger.commerce.customer.entity.CommerceCustomerAddress;
import com.flowledger.commerce.customer.entity.CommerceCustomerMembership;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.integration.entity.MerchantIntegrationProfile;
import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import org.springframework.stereotype.Component;

@Component
public class CommerceMapper {
    public CommerceDtos.CustomerResponse toCustomerResponse(CommerceCustomer customer) {
        return new CommerceDtos.CustomerResponse(
                customer.getId(),
                customer.getMobile(),
                customer.getEmail(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getDisplayName(),
                customer.getStatus().name(),
                customer.onboardingState().name());
    }

    public CommerceDtos.AddressResponse toAddressResponse(CommerceCustomerAddress address) {
        return new CommerceDtos.AddressResponse(
                address.getId(),
                address.getAddressType(),
                address.getHouse(),
                address.getStreet(),
                address.getLandmark(),
                address.getCity(),
                address.getDistrict(),
                address.getState(),
                address.getCountry(),
                address.getPincode(),
                address.getLatitude(),
                address.getLongitude(),
                address.isDefaultAddress());
    }

    public CommerceDtos.MembershipResponse toMembershipResponse(CommerceCustomerMembership membership) {
        return new CommerceDtos.MembershipResponse(
                membership.getId(),
                membership.getCustomerId(),
                membership.getOrganizationId(),
                membership.getFavoriteStoreId(),
                membership.getStatus(),
                membership.getJoinedAt());
    }

    public CommerceDtos.IntegrationProfileResponse toIntegrationResponse(MerchantIntegrationProfile profile) {
        return new CommerceDtos.IntegrationProfileResponse(
                profile.getMerchantType(),
                profile.getIntegrationType(),
                profile.getConnectorType(),
                profile.getStatus(),
                profile.getConfigurationJson(),
                profile.getHealthStatus(),
                profile.getLastSyncAt(),
                profile.getLastHealthCheckAt());
    }

    public CommerceDtos.CapabilityProfileResponse toCapabilityResponse(MerchantCapabilityProfile profile) {
        return new CommerceDtos.CapabilityProfileResponse(
                profile.isSupportsMarketplace(),
                profile.isSupportsDelivery(),
                profile.isSupportsPickup(),
                profile.isSupportsClickCollect(),
                profile.isSupportsScanAndGo(),
                profile.isSupportsScheduledDelivery(),
                profile.isSupportsScheduledPickup(),
                profile.isSupportsWallet(),
                profile.isSupportsCoupons(),
                profile.isSupportsLoyalty(),
                profile.isSupportsRecommendations(),
                profile.isSupportsReviews(),
                profile.isSupportsReturns(),
                profile.isSupportsGiftCards());
    }

    public CommerceDtos.StoreCommerceResponse toStoreCommerceResponse(StoreCommerceProfile profile) {
        return new CommerceDtos.StoreCommerceResponse(
                profile.getId(),
                profile.getStoreId(),
                profile.isCommerceEnabled(),
                profile.isAcceptOnlineOrders(),
                profile.isSupportsDelivery(),
                profile.isSupportsPickup(),
                profile.isSupportsClickCollect(),
                profile.isSupportsScanAndGo(),
                profile.isPublishedToMarketplace(),
                profile.isPublishProducts(),
                profile.isPublishInventory(),
                profile.isPublishPrices(),
                profile.getVisibility(),
                profile.getDiscoveryRadius(),
                profile.getStatus());
    }
}
