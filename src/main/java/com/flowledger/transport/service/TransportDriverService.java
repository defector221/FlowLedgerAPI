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
        TransportDriver e = new TransportDriver();
        e.setOrganizationId(org());
        apply(e, r);
        audit(e, true);
        return map(repository.save(e));
    }

    public DriverResponse update(UUID id, DriverRequest r) {
        TransportDriver e = load(id);
        if (!e.getLicenseNumber().equalsIgnoreCase(r.licenseNumber())
                && repository.existsByOrganizationIdAndLicenseNumberIgnoreCaseAndDeletedFalse(org(), r.licenseNumber()))
            conflict();
        apply(e, r);
        audit(e, false);
        return map(repository.save(e));
    }

    public void delete(UUID id) {
        TransportDriver e = load(id);
        e.setDeleted(true);
        e.setCurrentStatus(DriverStatus.INACTIVE);
        audit(e, false);
    }

    private void apply(TransportDriver e, DriverRequest r) {
        e.setCompanyId(r.companyId());
        e.setName(r.name());
        e.setLicenseNumber(r.licenseNumber());
        e.setLicenseExpiry(r.licenseExpiry());
        e.setMobile(r.mobile());
        e.setEmergencyContact(r.emergencyContact());
        e.setAssignedVehicleId(r.assignedVehicleId());
        e.setCurrentStatus(r.currentStatus() == null ? DriverStatus.AVAILABLE : r.currentStatus());
        e.setNotes(r.notes());
    }

    private TransportDriver load(UUID id) {
        return repository
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found"));
    }

    private DriverResponse map(TransportDriver e) {
        return new DriverResponse(
                e.getId(),
                e.getCompanyId(),
                e.getName(),
                e.getLicenseNumber(),
                e.getLicenseExpiry(),
                e.getMobile(),
                e.getEmergencyContact(),
                e.getAssignedVehicleId(),
                e.getCurrentStatus(),
                e.getNotes(),
                e.getVersion());
    }

    private UUID org() {
        return TenantContext.getOrganizationId();
    }

    private void audit(TransportDriver e, boolean c) {
        TenantContext.userId().ifPresent(u -> {
            if (c) e.setCreatedBy(u);
            e.setUpdatedBy(u);
        });
    }

    private void conflict() {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver license already exists");
    }
}
