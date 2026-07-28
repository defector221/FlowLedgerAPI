package com.flowledger.commerce.auth;

import com.flowledger.commerce.customer.entity.CommerceCustomer;
import com.flowledger.commerce.customer.repository.CommerceCustomerRepository;
import com.flowledger.common.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CommerceCustomerDetailsService {
    private final CommerceCustomerRepository customers;

    public CommerceCustomerDetailsService(CommerceCustomerRepository customers) {
        this.customers = customers;
    }

    public CommercePrincipal load(UUID customerId) {
        CommerceCustomer customer = customers
                .findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Commerce customer not found"));
        return new CommercePrincipal(customer.getId(), customer.getMobile());
    }
}
