package com.flowledger.commerce.wallet.repository;

import com.flowledger.commerce.wallet.entity.CommerceWalletEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceWalletEntryRepository extends JpaRepository<CommerceWalletEntry, UUID> {
    List<CommerceWalletEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
