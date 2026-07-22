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

    private final TaxRateRepository repo;
    private final TaxRateMapper mapper;

    public TaxRateService(TaxRateRepository repo, TaxRateMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public Response create(Create request) {
        if (repo.existsByOrganizationIdAndNameIgnoreCase(orgId(), request.name().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tax rate name already exists in this organization");
        }
        TaxRate taxRate = mapper.toEntity(request);
        taxRate.setName(request.name().trim());
        applyTypeAndSplit(
                taxRate,
                request.taxType(),
                request.splitStrategy(),
                request.cgstSharePercent(),
                request.sgstSharePercent());
        taxRate.setOrganizationId(orgId());
        calculate(taxRate);
        return mapper.toResponse(repo.save(taxRate));
    }

    @Transactional(readOnly = true)
    public List<Response> list() {
        return repo.findByOrganizationIdAndActiveTrue(orgId()).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return mapper.toResponse(load(id));
    }

    public Response update(UUID id, Update request) {
        TaxRate taxRate = load(id);
        mapper.update(request, taxRate);
        taxRate.setName(request.name().trim());
        applyTypeAndSplit(
                taxRate,
                request.taxType(),
                request.splitStrategy(),
                request.cgstSharePercent(),
                request.sgstSharePercent());
        calculate(taxRate);
        return mapper.toResponse(repo.save(taxRate));
    }

    public void delete(UUID id) {
        TaxRate taxRate = load(id);
        taxRate.setActive(false);
        repo.save(taxRate);
    }

    private void applyTypeAndSplit(
            TaxRate taxRate, TaxType taxType, SplitStrategy splitStrategy, BigDecimal cgstShare, BigDecimal sgstShare) {
        TaxType type = taxType == null ? (taxRate.getTaxType() == null ? TaxType.GST : taxRate.getTaxType()) : taxType;
        taxRate.setTaxType(type);

        SplitStrategy strategy = splitStrategy != null ? splitStrategy : SplitStrategy.defaultFor(type);
        taxRate.setSplitStrategy(strategy);

        if (strategy == SplitStrategy.NO_SPLIT_IGST || strategy == SplitStrategy.NO_SPLIT_OTHER) {
            taxRate.setCgstSharePercent(BigDecimal.ZERO);
            taxRate.setSgstSharePercent(BigDecimal.ZERO);
            return;
        }

        BigDecimal cgst = cgstShare != null ? cgstShare : defaultShare(taxRate.getCgstSharePercent());
        BigDecimal sgst = sgstShare != null ? sgstShare : defaultShare(taxRate.getSgstSharePercent());
        if (cgstShare == null && sgstShare == null && taxRate.getCgstSharePercent() == null) {
            cgst = new BigDecimal("50");
            sgst = new BigDecimal("50");
        } else if (cgstShare != null && sgstShare == null) {
            sgst = HUNDRED.subtract(cgst);
        } else if (sgstShare != null && cgstShare == null) {
            cgst = HUNDRED.subtract(sgst);
        }
        validateShares(cgst, sgst);
        taxRate.setCgstSharePercent(cgst);
        taxRate.setSgstSharePercent(sgst);
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

    private void calculate(TaxRate taxRate) {
        SplitStrategy strategy = taxRate.getSplitStrategy() == null
                ? SplitStrategy.defaultFor(taxRate.getTaxType())
                : taxRate.getSplitStrategy();
        BigDecimal rate = taxRate.getRate() == null ? BigDecimal.ZERO : taxRate.getRate();
        switch (strategy) {
            case NO_SPLIT_IGST -> {
                taxRate.setCgstRate(BigDecimal.ZERO);
                taxRate.setSgstRate(BigDecimal.ZERO);
                taxRate.setIgstRate(rate);
            }
            case NO_SPLIT_OTHER -> {
                taxRate.setCgstRate(BigDecimal.ZERO);
                taxRate.setSgstRate(BigDecimal.ZERO);
                taxRate.setIgstRate(BigDecimal.ZERO);
            }
            case PLACE_OF_SUPPLY, CUSTOM_PERCENT -> {
                BigDecimal cgstShare =
                        taxRate.getCgstSharePercent() == null ? new BigDecimal("50") : taxRate.getCgstSharePercent();
                BigDecimal sgstShare =
                        taxRate.getSgstSharePercent() == null ? new BigDecimal("50") : taxRate.getSgstSharePercent();
                taxRate.setCgstRate(rate.multiply(cgstShare).divide(HUNDRED, 4, RoundingMode.HALF_UP));
                taxRate.setSgstRate(rate.multiply(sgstShare).divide(HUNDRED, 4, RoundingMode.HALF_UP));
                taxRate.setIgstRate(rate);
            }
        }
        if (taxRate.getCessRate() == null) {
            taxRate.setCessRate(BigDecimal.ZERO);
        }
    }

    private TaxRate load(UUID id) {
        return required(repo.findByIdAndOrganizationId(id, orgId()), "Tax rate");
    }
}
