package com.flowledger.transport.repository;

import com.flowledger.transport.entity.IntegrationOutbox;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationOutboxRepository extends JpaRepository<IntegrationOutbox, UUID> {}
