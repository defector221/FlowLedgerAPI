package com.flowledger.commerce.customer.bridge;

import com.flowledger.commerce.common.CommerceTenantScope;
import com.flowledger.commerce.customer.entity.CommerceCustomer;
import com.flowledger.commerce.customer.repository.CommerceCustomerRepository;
import com.flowledger.customer.entity.Customer;
import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.common.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommerceCustomerBridgeService {
    private final CommerceCustomerRepository commerceCustomers;
    private final CustomerRepository erpCustomers;

    public CommerceCustomerBridgeService(
            CommerceCustomerRepository commerceCustomers, CustomerRepository erpCustomers) {
        this.commerceCustomers = commerceCustomers;
        this.erpCustomers = erpCustomers;
    }

    public UUID findOrCreateErpCustomer(UUID organizationId, UUID commerceCustomerId) {
        CommerceCustomer commerce = commerceCustomers
                .findById(commerceCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException("Commerce customer not found"));

        return CommerceTenantScope.run(organizationId, () -> {
            String code = "COMM-" + commerce.getMobile();
            return erpCustomers
                    .findByOrganizationIdAndCustomerCode(organizationId, code)
                    .map(Customer::getId)
                    .orElseGet(() -> createShadowCustomer(organizationId, commerce, code));
        });
    }

    private UUID createShadowCustomer(UUID organizationId, CommerceCustomer commerce, String code) {
        Customer customer = new Customer();
        customer.setOrganizationId(organizationId);
        customer.setCustomerCode(code);
        String name = commerce.getDisplayName() != null
                ? commerce.getDisplayName()
                : (commerce.getFirstName() != null ? commerce.getFirstName() : "Commerce Customer");
        customer.setCustomerName(name);
        customer.setPhone(commerce.getMobile());
        customer.setEmail(commerce.getEmail());
        return erpCustomers.save(customer).getId();
    }
}
