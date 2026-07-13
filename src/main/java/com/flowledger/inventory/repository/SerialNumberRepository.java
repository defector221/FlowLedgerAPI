package com.flowledger.inventory.repository;
import com.flowledger.inventory.entity.SerialNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SerialNumberRepository extends JpaRepository<SerialNumber,UUID> {
 Optional<SerialNumber> findByOrganizationIdAndProductIdAndSerialNumber(UUID org,UUID product,String serial);
}
