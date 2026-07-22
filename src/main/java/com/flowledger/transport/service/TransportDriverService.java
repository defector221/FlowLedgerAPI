package com.flowledger.transport.service;

import static com.flowledger.transport.dto.TransportDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.transport.domain.TransportEnums.DriverStatus;
import com.flowledger.transport.entity.TransportDriver;
import com.flowledger.transport.repository.TransportDriverRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TransportDriverService {
    private final TransportDriverRepository repository;

    public TransportDriverService(TransportDriverRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> list() {
        return repository.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org()).stream()
                .map(this::map)
                .toList();
    }

    @Transactional(readOnly = true)
    public DriverResponse get(UUID id) {
        return map(load(id));
    }

    public DriverResponse create(DriverRequest r) {
        if (repository.existsByOrganizationIdAndLicenseNumberIgnoreCaseAndDeletedFalse(org(), r.licenseNumber()))
            conflict();
        TransportDriver driver = new TransportDriver();
        driver.setOrganizationId(org());
        apply(driver, r);
        audit(driver, true);
        return map(repository.save(driver));
    }

    public DriverResponse update(UUID id, DriverRequest r) {
        TransportDriver driver = load(id);
        if (!driver.getLicenseNumber().equalsIgnoreCase(r.licenseNumber())
                && repository.existsByOrganizationIdAndLicenseNumberIgnoreCaseAndDeletedFalse(org(), r.licenseNumber()))
            conflict();
        apply(driver, r);
        audit(driver, false);
        return map(repository.save(driver));
    }

    public void delete(UUID id) {
        TransportDriver driver = load(id);
        driver.setDeleted(true);
        driver.setCurrentStatus(DriverStatus.INACTIVE);
        audit(driver, false);
    }

    private void apply(TransportDriver driver, DriverRequest r) {
        driver.setCompanyId(r.companyId());
        driver.setName(r.name());
        driver.setLicenseNumber(r.licenseNumber());
        driver.setLicenseExpiry(r.licenseExpiry());
        driver.setMobile(r.mobile());
        driver.setEmergencyContact(r.emergencyContact());
        driver.setAssignedVehicleId(r.assignedVehicleId());
        driver.setCurrentStatus(r.currentStatus() == null ? DriverStatus.AVAILABLE : r.currentStatus());
        driver.setNotes(r.notes());
    }

    private TransportDriver load(UUID id) {
        return repository
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found"));
    }

    private DriverResponse map(TransportDriver driver) {
        return new DriverResponse(
                driver.getId(),
                driver.getCompanyId(),
                driver.getName(),
                driver.getLicenseNumber(),
                driver.getLicenseExpiry(),
                driver.getMobile(),
                driver.getEmergencyContact(),
                driver.getAssignedVehicleId(),
                driver.getCurrentStatus(),
                driver.getNotes(),
                driver.getVersion());
    }

    private UUID org() {
        return TenantContext.getOrganizationId();
    }

    private void audit(TransportDriver driver, boolean created) {
        TenantContext.userId().ifPresent(u -> {
            if (created) driver.setCreatedBy(u);
            driver.setUpdatedBy(u);
        });
    }

    private void conflict() {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver license already exists");
    }
}
