package com.flowledger.transport.repository;

import com.flowledger.transport.entity.TransportCompanyBranch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportCompanyBranchRepository extends JpaRepository<TransportCompanyBranch, UUID> {}
