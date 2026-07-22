package com.flowledger.transport.repository;

import com.flowledger.transport.entity.TransportServiceArea;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportServiceAreaRepository extends JpaRepository<TransportServiceArea, UUID> {}
