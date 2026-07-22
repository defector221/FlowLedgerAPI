package com.flowledger.transport.repository;

import com.flowledger.transport.entity.TransportCompanyContact;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportCompanyContactRepository extends JpaRepository<TransportCompanyContact, UUID> {}
