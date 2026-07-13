package com.flowledger.sales.repository;

import com.flowledger.sales.entity.CreditNote;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {
    Optional<CreditNote> findByIdAndOrganizationId(UUID id, UUID org);

    List<CreditNote> findByOrganizationIdOrderByCreditNoteDateDesc(UUID org);
}
