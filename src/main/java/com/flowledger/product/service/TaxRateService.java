package com.flowledger.product.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.product.dto.TaxRateDtos.*;
import com.flowledger.product.entity.TaxRate;
import com.flowledger.product.entity.TaxType;
import com.flowledger.product.mapper.TaxRateMapper;
import com.flowledger.product.repository.TaxRateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        if (r.existsByOrganizationIdAndNameIgnoreCase(orgId(), d.name().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tax rate name already exists in this organization");
        }
        TaxRate e = m.toEntity(d);
        e.setName(d.name().trim());
        e.setTaxType(d.taxType() == null ? TaxType.GST : d.taxType());
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
        e.setName(d.name().trim());
        if (d.taxType() != null) {
            e.setTaxType(d.taxType());
        }
        calculate(e);
        return m.toResponse(r.save(e));
    }

    public void delete(UUID id) {
        r.delete(load(id));
    }

    private void calculate(TaxRate e) {
        TaxType type = e.getTaxType() == null ? TaxType.GST : e.getTaxType();
        BigDecimal rate = e.getRate() == null ? BigDecimal.ZERO : e.getRate();
        switch (type) {
            case IGST -> {
                e.setCgstRate(BigDecimal.ZERO);
                e.setSgstRate(BigDecimal.ZERO);
                e.setIgstRate(rate);
            }
            case OTHER -> {
                // Flat / non-GST tax — rate is applied as-is, no CGST/SGST/IGST components.
                e.setCgstRate(BigDecimal.ZERO);
                e.setSgstRate(BigDecimal.ZERO);
                e.setIgstRate(BigDecimal.ZERO);
            }
            case GST -> {
                BigDecimal half = rate.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
                e.setCgstRate(half);
                e.setSgstRate(half);
                e.setIgstRate(rate);
            }
        }
        if (e.getCessRate() == null) {
            e.setCessRate(BigDecimal.ZERO);
        }
    }

    private TaxRate load(UUID id) {
        return required(r.findByIdAndOrganizationId(id, orgId()), "Tax rate");
    }
}
