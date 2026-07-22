package com.flowledger.transport.service;

import static com.flowledger.transport.dto.TransportDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.transport.domain.TransportEnums.VehicleStatus;
import com.flowledger.transport.entity.TransportVehicle;
import com.flowledger.transport.repository.TransportVehicleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TransportVehicleService {
    private final TransportVehicleRepository repository;

    public TransportVehicleService(TransportVehicleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> list() {
        return repository.findByOrganizationIdAndDeletedFalseOrderByVehicleNumberAsc(org()).stream()
                .map(this::map)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse get(UUID id) {
        return map(load(id));
    }

    public VehicleResponse create(VehicleRequest r) {
        if (repository.existsByOrganizationIdAndVehicleNumberIgnoreCaseAndDeletedFalse(org(), r.vehicleNumber()))
            conflict();
        TransportVehicle e = new TransportVehicle();
        e.setOrganizationId(org());
        apply(e, r);
        audit(e, true);
        return map(repository.save(e));
    }

    public VehicleResponse update(UUID id, VehicleRequest r) {
        TransportVehicle e = load(id);
        if (!e.getVehicleNumber().equalsIgnoreCase(r.vehicleNumber())
                && repository.existsByOrganizationIdAndVehicleNumberIgnoreCaseAndDeletedFalse(org(), r.vehicleNumber()))
            conflict();
        apply(e, r);
        audit(e, false);
        return map(repository.save(e));
    }

    public void delete(UUID id) {
        TransportVehicle e = load(id);
        e.setDeleted(true);
        e.setCurrentStatus(VehicleStatus.INACTIVE);
        audit(e, false);
    }

    private void apply(TransportVehicle e, VehicleRequest r) {
        e.setCompanyId(r.companyId());
        e.setVehicleNumber(r.vehicleNumber());
        e.setVehicleType(r.vehicleType());
        e.setCapacity(r.capacity());
        e.setCapacityUnit(r.capacityUnit());
        e.setOwnership(r.ownership());
        e.setDriverId(r.driverId());
        e.setFitnessExpiry(r.fitnessExpiry());
        e.setInsuranceExpiry(r.insuranceExpiry());
        e.setPermitExpiry(r.permitExpiry());
        e.setCurrentStatus(r.currentStatus() == null ? VehicleStatus.AVAILABLE : r.currentStatus());
        e.setNotes(r.notes());
    }

    private TransportVehicle load(UUID id) {
        return repository
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
    }

    private VehicleResponse map(TransportVehicle e) {
        return new VehicleResponse(
                e.getId(),
                e.getCompanyId(),
                e.getVehicleNumber(),
                e.getVehicleType(),
                e.getCapacity(),
                e.getCapacityUnit(),
                e.getOwnership(),
                e.getDriverId(),
                e.getFitnessExpiry(),
                e.getInsuranceExpiry(),
                e.getPermitExpiry(),
                e.getCurrentStatus(),
                e.getNotes(),
                e.getVersion());
    }

    private UUID org() {
        return TenantContext.getOrganizationId();
    }

    private void audit(TransportVehicle e, boolean c) {
        TenantContext.userId().ifPresent(u -> {
            if (c) e.setCreatedBy(u);
            e.setUpdatedBy(u);
        });
    }

    private void conflict() {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle number already exists");
    }
}
