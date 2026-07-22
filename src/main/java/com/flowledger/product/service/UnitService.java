package com.flowledger.product.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.product.dto.UnitDtos.*;
import com.flowledger.product.entity.Unit;
import com.flowledger.product.mapper.UnitMapper;
import com.flowledger.product.repository.UnitRepository;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.*;

@Service
@Transactional
public class UnitService extends OrganizationScopedService {
    private final UnitRepository repo;
    private final UnitMapper mapper;

    public UnitService(UnitRepository repo, UnitMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<Response> list() {
        return repo.findBySystemUnitTrueOrOrganizationId(orgId()).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public Response create(Create request) {
        if (repo.existsByOrganizationIdAndCode(orgId(), request.code()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unit code already exists");
        Unit unit = mapper.toEntity(request);
        unit.setOrganizationId(orgId());
        unit.setSystemUnit(false);
        return mapper.toResponse(repo.save(unit));
    }
}
