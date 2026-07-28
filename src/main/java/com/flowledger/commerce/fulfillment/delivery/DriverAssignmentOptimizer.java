package com.flowledger.commerce.fulfillment.delivery;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DriverAssignmentOptimizer {
    public UUID suggestDriver(List<UUID> availableDrivers, UUID storeId) {
        if (availableDrivers == null || availableDrivers.isEmpty()) {
            return null;
        }
        return availableDrivers.get(0);
    }
}
