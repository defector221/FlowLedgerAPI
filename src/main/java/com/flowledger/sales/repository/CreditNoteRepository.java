package com.flowledger.sales.repository;

import com.flowledger.sales.entity.CreditNote;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {
    Optional<CreditNote> findByIdAndOrganizationId(UUID id, UUID org);

    Page<CreditNote> findByOrganizationIdOrderByCreditNoteDateDesc(UUID org, Pageable pageable);
}
