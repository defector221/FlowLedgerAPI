package com.flowledger.accounting.repository;

import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.accounting.entity.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByOrganizationIdOrderByAccountCodeAsc(UUID organizationId);

    Optional<Account> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Account> findByOrganizationIdAndSystemAccountKey(UUID organizationId, SystemAccountKey key);

    Optional<Account> findByOrganizationIdAndAccountCode(UUID organizationId, String accountCode);

    boolean existsByOrganizationIdAndAccountCode(UUID organizationId, String accountCode);

    boolean existsByOrganizationIdAndSystemAccountKeyIsNotNull(UUID organizationId);
}
