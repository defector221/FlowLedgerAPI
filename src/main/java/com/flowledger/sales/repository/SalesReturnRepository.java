package com.flowledger.sales.repository;

import com.flowledger.sales.entity.SalesReturn;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, UUID> {
    Optional<SalesReturn> findByIdAndOrganizationId(UUID id, UUID org);

    Page<SalesReturn> findByOrganizationIdOrderByReturnDateDesc(UUID org, Pageable pageable);
}
