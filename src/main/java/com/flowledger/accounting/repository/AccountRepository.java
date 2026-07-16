package com.flowledger.accounting.repository;

import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.accounting.entity.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByOrganizationIdOrderByAccountCodeAsc(UUID organizationId);

    List<Account> findByOrganizationIdAndStatusOrderByAccountCodeAsc(
            UUID organizationId, com.flowledger.accounting.domain.AccountStatus status);

    Optional<Account> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Account> findByOrganizationIdAndSystemAccountKey(UUID organizationId, SystemAccountKey key);

    Optional<Account> findByOrganizationIdAndAccountCode(UUID organizationId, String accountCode);

    boolean existsByOrganizationIdAndAccountCode(UUID organizationId, String accountCode);

    boolean existsByOrganizationIdAndSystemAccountKeyIsNotNull(UUID organizationId);

    boolean existsByOrganizationIdAndParentAccountId(UUID organizationId, UUID parentAccountId);

    boolean existsByOrganizationIdAndParentAccountIdAndAccountNameIgnoreCase(
            UUID organizationId, UUID parentAccountId, String accountName);

    @Query(
            """
            select count(a) > 0 from Account a
            where a.organizationId = :orgId
              and a.parentAccountId = :parentId
              and lower(a.accountName) = lower(:name)
              and a.id <> :excludeId
            """)
    boolean existsSiblingName(
            @Param("orgId") UUID orgId,
            @Param("parentId") UUID parentId,
            @Param("name") String name,
            @Param("excludeId") UUID excludeId);
}
