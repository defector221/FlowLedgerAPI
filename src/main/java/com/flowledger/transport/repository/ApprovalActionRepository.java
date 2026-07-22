package com.flowledger.transport.repository;

import com.flowledger.transport.entity.ApprovalAction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, UUID> {}
