package com.flowledger.product.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.product.dto.TaxRateDtos.*;
import com.flowledger.product.entity.SplitStrategy;
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
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal SHARE_TOLERANCE = new BigDecimal("0.01");

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
        applyTypeAndSplit(e, d.taxType(), d.splitStrategy(), d.cgstSharePercent(), d.sgstSharePercent());
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
        applyTypeAndSplit(e, d.taxType(), d.splitStrategy(), d.cgstSharePercent(), d.sgstSharePercent());
        calculate(e);
        return m.toResponse(r.save(e));
    }

    public void delete(UUID id) {
        TaxRate e = load(id);
        e.setActive(false);
        r.save(e);
    }

    private void applyTypeAndSplit(
            TaxRate e, TaxType taxType, SplitStrategy splitStrategy, BigDecimal cgstShare, BigDecimal sgstShare) {
        TaxType type = taxType == null ? (e.getTaxType() == null ? TaxType.GST : e.getTaxType()) : taxType;
        e.setTaxType(type);

        SplitStrategy strategy = splitStrategy != null ? splitStrategy : SplitStrategy.defaultFor(type);
        e.setSplitStrategy(strategy);

        if (strategy == SplitStrategy.NO_SPLIT_IGST || strategy == SplitStrategy.NO_SPLIT_OTHER) {
            e.setCgstSharePercent(BigDecimal.ZERO);
            e.setSgstSharePercent(BigDecimal.ZERO);
            return;
        }

        BigDecimal cgst = cgstShare != null ? cgstShare : defaultShare(e.getCgstSharePercent());
        BigDecimal sgst = sgstShare != null ? sgstShare : defaultShare(e.getSgstSharePercent());
        if (cgstShare == null && sgstShare == null && e.getCgstSharePercent() == null) {
            cgst = new BigDecimal("50");
            sgst = new BigDecimal("50");
        } else if (cgstShare != null && sgstShare == null) {
            sgst = HUNDRED.subtract(cgst);
        } else if (sgstShare != null && cgstShare == null) {
            cgst = HUNDRED.subtract(sgst);
        }
        validateShares(cgst, sgst);
        e.setCgstSharePercent(cgst);
        e.setSgstSharePercent(sgst);
    }

    private static BigDecimal defaultShare(BigDecimal existing) {
        return existing == null ? new BigDecimal("50") : existing;
    }

    private static void validateShares(BigDecimal cgst, BigDecimal sgst) {
        if (cgst.compareTo(BigDecimal.ZERO) < 0 || sgst.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Share percents cannot be negative");
        }
        BigDecimal sum = cgst.add(sgst);
        if (sum.subtract(HUNDRED).abs().compareTo(SHARE_TOLERANCE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CGST and SGST share percents must sum to 100");
        }
    }

    private void calculate(TaxRate e) {
        SplitStrategy strategy =
                e.getSplitStrategy() == null ? SplitStrategy.defaultFor(e.getTaxType()) : e.getSplitStrategy();
        BigDecimal rate = e.getRate() == null ? BigDecimal.ZERO : e.getRate();
        switch (strategy) {
            case NO_SPLIT_IGST -> {
                e.setCgstRate(BigDecimal.ZERO);
                e.setSgstRate(BigDecimal.ZERO);
                e.setIgstRate(rate);
            }
            case NO_SPLIT_OTHER -> {
                e.setCgstRate(BigDecimal.ZERO);
                e.setSgstRate(BigDecimal.ZERO);
                e.setIgstRate(BigDecimal.ZERO);
            }
            case PLACE_OF_SUPPLY, CUSTOM_PERCENT -> {
                BigDecimal cgstShare = e.getCgstSharePercent() == null ? new BigDecimal("50") : e.getCgstSharePercent();
                BigDecimal sgstShare = e.getSgstSharePercent() == null ? new BigDecimal("50") : e.getSgstSharePercent();
                e.setCgstRate(rate.multiply(cgstShare).divide(HUNDRED, 4, RoundingMode.HALF_UP));
                e.setSgstRate(rate.multiply(sgstShare).divide(HUNDRED, 4, RoundingMode.HALF_UP));
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
