package com.flowledger.commerce.service;

import com.flowledger.commerce.auth.CommerceSecurityContext;
import com.flowledger.commerce.customer.entity.CommerceCustomer;
import com.flowledger.commerce.customer.entity.CommerceCustomerAddress;
import com.flowledger.commerce.customer.entity.CommerceCustomerMembership;
import com.flowledger.commerce.customer.entity.CommerceCustomerPreferences;
import com.flowledger.commerce.customer.repository.CommerceCustomerAddressRepository;
import com.flowledger.commerce.customer.repository.CommerceCustomerMembershipRepository;
import com.flowledger.commerce.customer.repository.CommerceCustomerPreferencesRepository;
import com.flowledger.commerce.customer.repository.CommerceCustomerRepository;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.events.CustomerRegisteredEvent;
import com.flowledger.commerce.mapper.CommerceMapper;
import com.flowledger.commerce.config.CommerceModuleGuard;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.platform.event.DomainEventPublisher;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommerceCustomerService {
    private final CommerceCustomerRepository customers;
    private final CommerceCustomerAddressRepository addresses;
    private final CommerceCustomerPreferencesRepository preferencesRepository;
    private final CommerceCustomerMembershipRepository memberships;
    private final CommerceModuleGuard guard;
    private final CommerceMapper mapper;
    private final DomainEventPublisher events;

    public CommerceCustomerService(
            CommerceCustomerRepository customers,
            CommerceCustomerAddressRepository addresses,
            CommerceCustomerPreferencesRepository preferencesRepository,
            CommerceCustomerMembershipRepository memberships,
            CommerceModuleGuard guard,
            CommerceMapper mapper,
            DomainEventPublisher events) {
        this.customers = customers;
        this.addresses = addresses;
        this.preferencesRepository = preferencesRepository;
        this.memberships = memberships;
        this.guard = guard;
        this.mapper = mapper;
        this.events = events;
    }

    public CommerceDtos.CustomerResponse register(CommerceDtos.RegisterCustomerRequest request) {
        if (customers.existsByMobile(request.mobile())) {
            throw new BusinessException("Mobile already registered");
        }
        if (request.email() != null && customers.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessException("Email already registered");
        }
        CommerceCustomer customer = new CommerceCustomer();
        customer.setMobile(request.mobile());
        customer.setEmail(request.email());
        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customers.save(customer);
        events.publish(new CustomerRegisteredEvent(this, customer.getId()));
        if (request.organizationId() != null) {
            ensureMembership(customer.getId(), request.organizationId());
        }
        return mapper.toCustomerResponse(customer);
    }

    @Transactional(readOnly = true)
    public CommerceDtos.CustomerResponse me() {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        return mapper.toCustomerResponse(customers
                .findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found")));
    }

    public CommerceDtos.CustomerResponse updateMe(CommerceDtos.UpdateCustomerRequest request) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceCustomer customer = customers
                .findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (request.email() != null) customer.setEmail(request.email());
        customer.completeProfile(request.firstName(), request.lastName(), request.displayName());
        if (request.displayName() != null) customer.setDisplayName(request.displayName());
        if (request.preferredLanguage() != null) customer.setPreferredLanguage(request.preferredLanguage());
        if (request.marketingConsent() != null) customer.setMarketingConsent(request.marketingConsent());
        if (request.notificationConsent() != null) customer.setNotificationConsent(request.notificationConsent());
        customers.save(customer);
        return mapper.toCustomerResponse(customer);
    }

    public CommerceDtos.AddressResponse addAddress(CommerceDtos.AddressRequest request) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        if (Boolean.TRUE.equals(request.defaultAddress())) {
            addresses.findByCustomerIdOrderByDefaultAddressDescCreatedAtAsc(customerId).forEach(a -> {
                a.setDefaultAddress(false);
                addresses.save(a);
            });
        }
        CommerceCustomerAddress address = new CommerceCustomerAddress();
        address.setCustomerId(customerId);
        applyAddress(address, request);
        return mapper.toAddressResponse(addresses.save(address));
    }

    public CommerceDtos.AddressResponse updateAddress(UUID addressId, CommerceDtos.AddressRequest request) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceCustomerAddress address = addresses
                .findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        applyAddress(address, request);
        return mapper.toAddressResponse(addresses.save(address));
    }

    public void deleteAddress(UUID addressId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceCustomerAddress address = addresses
                .findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        addresses.delete(address);
    }

    @Transactional(readOnly = true)
    public List<CommerceDtos.AddressResponse> listAddresses() {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        return addresses.findByCustomerIdOrderByDefaultAddressDescCreatedAtAsc(customerId).stream()
                .map(mapper::toAddressResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CommerceDtos.MembershipResponse> listMemberships(Pageable pageable) {
        UUID orgId = guard.ensureEnabled();
        return memberships.findByOrganizationId(orgId, pageable).map(mapper::toMembershipResponse);
    }

    public CommerceDtos.MembershipResponse updateMembershipStatus(UUID membershipId, String status) {
        UUID orgId = guard.ensureEnabled();
        CommerceCustomerMembership membership = memberships
                .findByIdAndOrganizationId(membershipId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));
        membership.setStatus(status);
        return mapper.toMembershipResponse(memberships.save(membership));
    }

    private void ensureMembership(UUID customerId, UUID organizationId) {
        memberships.findByCustomerIdAndOrganizationId(customerId, organizationId).orElseGet(() -> {
            CommerceCustomerMembership membership = new CommerceCustomerMembership();
            membership.setCustomerId(customerId);
            membership.setOrganizationId(organizationId);
            return memberships.save(membership);
        });
    }

    private static void applyAddress(CommerceCustomerAddress address, CommerceDtos.AddressRequest request) {
        if (request.addressType() != null) address.setAddressType(request.addressType());
        if (request.house() != null) address.setHouse(request.house());
        if (request.street() != null) address.setStreet(request.street());
        if (request.landmark() != null) address.setLandmark(request.landmark());
        if (request.city() != null) address.setCity(request.city());
        if (request.district() != null) address.setDistrict(request.district());
        if (request.state() != null) address.setState(request.state());
        if (request.country() != null) address.setCountry(request.country());
        if (request.pincode() != null) address.setPincode(request.pincode());
        if (request.latitude() != null) address.setLatitude(request.latitude());
        if (request.longitude() != null) address.setLongitude(request.longitude());
        if (request.defaultAddress() != null) address.setDefaultAddress(request.defaultAddress());
    }
}
