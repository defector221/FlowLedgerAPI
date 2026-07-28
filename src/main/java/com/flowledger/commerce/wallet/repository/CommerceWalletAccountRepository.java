package com.flowledger.commerce.wallet.repository;

import com.flowledger.commerce.wallet.domain.WalletAccountType;
import com.flowledger.commerce.wallet.entity.CommerceWalletAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceWalletAccountRepository extends JpaRepository<CommerceWalletAccount, UUID> {
    Optional<CommerceWalletAccount> findByCustomerIdAndOrganizationIdAndAccountTypeAndCurrency(
            UUID customerId, UUID organizationId, WalletAccountType accountType, String currency);

    List<CommerceWalletAccount> findByCustomerIdAndOrganizationId(UUID customerId, UUID organizationId);
}
