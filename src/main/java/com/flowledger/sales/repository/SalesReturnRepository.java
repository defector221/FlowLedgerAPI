package com.flowledger.sales.repository;

import com.flowledger.sales.entity.SalesReturn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, UUID> {
    Optional<SalesReturn> findByIdAndOrganizationId(UUID id, UUID org);

    List<SalesReturn> findByOrganizationIdOrderByReturnDateDesc(UUID org);
}
