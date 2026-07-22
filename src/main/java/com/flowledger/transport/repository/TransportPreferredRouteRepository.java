package com.flowledger.transport.repository;

import com.flowledger.transport.entity.TransportPreferredRoute;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportPreferredRouteRepository extends JpaRepository<TransportPreferredRoute, UUID> {}
