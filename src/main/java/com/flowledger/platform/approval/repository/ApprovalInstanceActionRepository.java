package com.flowledger.platform.approval.repository;

import com.flowledger.platform.approval.entity.ApprovalInstanceAction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalInstanceActionRepository extends JpaRepository<ApprovalInstanceAction, UUID> {
    List<ApprovalInstanceAction> findByInstanceIdOrderByActedAtAsc(UUID instanceId);
}
