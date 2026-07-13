package com.flowledger.inventory.repository;

import com.flowledger.inventory.entity.SerialNumber;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SerialNumberRepository extends JpaRepository<SerialNumber, UUID> {
    Optional<SerialNumber> findByOrganizationIdAndProductIdAndSerialNumber(UUID org, UUID product, String serial);
}
