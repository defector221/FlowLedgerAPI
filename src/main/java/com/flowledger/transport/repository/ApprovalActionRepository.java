package com.flowledger.transport.repository;

import com.flowledger.transport.entity.ApprovalAction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, UUID> {
    List<ApprovalAction> findByRequestIdOrderByActedAtAsc(UUID requestId);
}
