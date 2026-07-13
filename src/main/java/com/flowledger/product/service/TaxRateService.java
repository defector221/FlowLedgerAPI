package com.flowledger.product.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.product.dto.TaxRateDtos.*;
import com.flowledger.product.entity.TaxRate;
import com.flowledger.product.mapper.TaxRateMapper;
import com.flowledger.product.repository.TaxRateRepository;
import java.math.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.*;

@Service
@Transactional
public class TaxRateService extends OrganizationScopedService {
    private final TaxRateRepository r;
    private final TaxRateMapper m;

    public TaxRateService(TaxRateRepository r, TaxRateMapper m) {
        this.r = r;
        this.m = m;
    }

    public Response create(Create d) {
        if (r.existsByOrganizationIdAndName(orgId(), d.name()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tax rate name already exists");
        TaxRate e = m.toEntity(d);
        e.setOrganizationId(orgId());
        calculate(e);
        return m.toResponse(r.save(e));
    }

    @Transactional(readOnly = true)
    public List<Response> list() {
        return r.findByOrganizationIdAndActiveTrue(orgId()).stream()
                .map(m::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return m.toResponse(load(id));
    }

    public Response update(UUID id, Update d) {
        TaxRate e = load(id);
        m.update(d, e);
        calculate(e);
        return m.toResponse(r.save(e));
    }

    public void delete(UUID id) {
        r.delete(load(id));
    }

    private void calculate(TaxRate e) {
        BigDecimal half = e.getRate().divide(BigDecimal.valueOf(2), 4, java.math.RoundingMode.HALF_UP);
        e.setCgstRate(half);
        e.setSgstRate(half);
        e.setIgstRate(e.getRate());
        if (e.getCessRate() == null) e.setCessRate(BigDecimal.ZERO);
    }

    private TaxRate load(UUID id) {
        return required(r.findByIdAndOrganizationId(id, orgId()), "Tax rate");
    }
}
