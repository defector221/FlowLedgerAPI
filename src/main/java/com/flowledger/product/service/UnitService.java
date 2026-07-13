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
    private final UnitRepository r;
    private final UnitMapper m;

    public UnitService(UnitRepository r, UnitMapper m) {
        this.r = r;
        this.m = m;
    }

    @Transactional(readOnly = true)
    public List<Response> list() {
        return r.findBySystemUnitTrueOrOrganizationId(orgId()).stream()
                .map(m::toResponse)
                .toList();
    }

    public Response create(Create d) {
        if (r.existsByOrganizationIdAndCode(orgId(), d.code()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unit code already exists");
        Unit e = m.toEntity(d);
        e.setOrganizationId(orgId());
        e.setSystemUnit(false);
        return m.toResponse(r.save(e));
    }
}
