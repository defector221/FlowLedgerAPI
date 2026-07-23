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
        TransportVehicle vehicle = new TransportVehicle();
        vehicle.setOrganizationId(org());
        apply(vehicle, r);
        audit(vehicle, true);
        return map(repository.save(vehicle));
    }

    public VehicleResponse update(UUID id, VehicleRequest r) {
        TransportVehicle vehicle = load(id);
        if (!vehicle.getVehicleNumber().equalsIgnoreCase(r.vehicleNumber())
                && repository.existsByOrganizationIdAndVehicleNumberIgnoreCaseAndDeletedFalse(org(), r.vehicleNumber()))
            conflict();
        apply(vehicle, r);
        audit(vehicle, false);
        return map(repository.save(vehicle));
    }

    public void delete(UUID id) {
        TransportVehicle vehicle = load(id);
        vehicle.setDeleted(true);
        vehicle.setCurrentStatus(VehicleStatus.INACTIVE);
        audit(vehicle, false);
    }

    private void apply(TransportVehicle vehicle, VehicleRequest r) {
        vehicle.setCompanyId(r.companyId());
        vehicle.setVehicleNumber(r.vehicleNumber());
        vehicle.setVehicleType(r.vehicleType());
        vehicle.setCapacity(r.capacity());
        vehicle.setCapacityUnit(r.capacityUnit());
        vehicle.setOwnership(r.ownership());
        vehicle.setDriverId(r.driverId());
        vehicle.setFitnessExpiry(r.fitnessExpiry());
        vehicle.setInsuranceExpiry(r.insuranceExpiry());
        vehicle.setPermitExpiry(r.permitExpiry());
        vehicle.setCurrentStatus(r.currentStatus() == null ? VehicleStatus.AVAILABLE : r.currentStatus());
        vehicle.setNotes(r.notes());
    }

    private TransportVehicle load(UUID id) {
        return repository
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
    }

    private VehicleResponse map(TransportVehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getCompanyId(),
                vehicle.getVehicleNumber(),
                vehicle.getVehicleType(),
                vehicle.getCapacity(),
                vehicle.getCapacityUnit(),
                vehicle.getOwnership(),
                vehicle.getDriverId(),
                vehicle.getFitnessExpiry(),
                vehicle.getInsuranceExpiry(),
                vehicle.getPermitExpiry(),
                vehicle.getCurrentStatus(),
                vehicle.getNotes(),
                vehicle.getVersion());
    }

    private UUID org() {
        return TenantContext.getOrganizationId();
    }

    private void audit(TransportVehicle vehicle, boolean created) {
        TenantContext.userId().ifPresent(u -> {
            if (created) vehicle.setCreatedBy(u);
            vehicle.setUpdatedBy(u);
        });
    }

    private void conflict() {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle number already exists");
    }
}
