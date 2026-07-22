package com.flowledger.transport.repository;

import com.flowledger.transport.entity.TransportRateCard;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportRateCardRepository extends JpaRepository<TransportRateCard, UUID> {}
