package com.flowledger.commerce.connector.repository;

import com.flowledger.commerce.connector.entity.CommerceConnectorSyncDlq;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceConnectorSyncDlqRepository extends JpaRepository<CommerceConnectorSyncDlq, UUID> {}
