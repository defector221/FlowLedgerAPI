package com.flowledger.warehouse.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.util.EntityCodeGenerator;
import com.flowledger.warehouse.dto.WarehouseDtos.*;
import com.flowledger.warehouse.entity.Warehouse;
import com.flowledger.warehouse.mapper.WarehouseMapper;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class WarehouseService extends OrganizationScopedService {
    private final WarehouseRepository repo;
    private final WarehouseMapper mapper;

    public WarehouseService(WarehouseRepository repo, WarehouseMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public Response create(Create dto) {
        UUID org = orgId();
        String code = resolveCode(org, dto.warehouseCode(), dto.warehouseName());
        if (repo.existsByOrganizationIdAndWarehouseCode(org, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Warehouse code already exists");
        }
        if (Boolean.TRUE.equals(dto.defaultWarehouse())) {
            repo.clearDefault(org);
        }
        Warehouse warehouse = mapper.toEntity(dto);
        warehouse.setWarehouseCode(code);
        warehouse.setOrganizationId(org);
        if (dto.defaultWarehouse() != null) {
            warehouse.setDefaultWarehouse(dto.defaultWarehouse());
        }
        return mapper.toResponse(repo.save(warehouse));
    }

    private String resolveCode(UUID org, String provided, String name) {
        if (provided != null && !provided.isBlank()) {
            return provided.trim().toUpperCase(Locale.ROOT);
        }
        return EntityCodeGenerator.uniqueFromName(
                name, "WH", candidate -> repo.existsByOrganizationIdAndWarehouseCode(org, candidate));
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return mapper.toResponse(load(id));
    }

    public Response update(UUID id, Update dto) {
        Warehouse warehouse = load(id);
        mapper.update(dto, warehouse);
        if (dto.active() != null) {
            warehouse.setActive(dto.active());
        }
        return mapper.toResponse(repo.save(warehouse));
    }

    @Transactional(readOnly = true)
    public List<Response> list() {
        return repo.findByOrganizationId(orgId()).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private Warehouse load(UUID id) {
        return required(repo.findByIdAndOrganizationId(id, orgId()), "Warehouse");
    }
}
