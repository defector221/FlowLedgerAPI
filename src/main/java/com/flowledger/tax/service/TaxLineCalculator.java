package com.flowledger.tax.service;

import com.flowledger.product.entity.SplitStrategy;
import com.flowledger.product.entity.TaxType;
import com.flowledger.tax.dto.GstCalculationDtos.Request;
import com.flowledger.tax.dto.GstCalculationDtos.Response;
import com.flowledger.tax.dto.TaxCalculationDtos.TaxCalculationRequest;
import com.flowledger.tax.dto.TaxCalculationDtos.TaxResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TaxLineCalculator {
    private final TaxCalculationService taxCalculationService;

    public TaxLineCalculator(TaxCalculationService taxCalculationService) {
        this.taxCalculationService = taxCalculationService;
    }

    public Response calculate(Request request) {
        return toGstResponse(calculateResult(request));
    }

    public TaxResult calculateDocumentLine(DocumentLineInput input) {
        if (input.productId() != null || input.taxCategoryId() != null) {
            try {
                return taxCalculationService.calculateTax(toEngineRequest(input));
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                    throw ex;
                }
            }
        }
        return calculateResult(toLegacyRequest(input));
    }

    public TaxResult calculateResult(Request request) {
        return taxCalculationService.calculateWithRule(
                new TaxCalculationRequest(
                        request.organizationStateCode(),
                        request.placeOfSupplyStateCode(),
                        "IN",
                        null,
                        null,
                        null,
                        null,
                        request.quantity(),
                        request.rate(),
                        request.discount(),
                        request.taxInclusive(),
                        request.taxType(),
                        request.splitStrategy(),
                        request.cgstSharePercent(),
                        request.sgstSharePercent()),
                null,
                legacyRule(request));
    }

    public void applySnapshot(LineTaxAmounts line, Response response) {
        line.setTaxableAmount(response.taxable());
        line.setCgstAmount(response.cgst());
        line.setSgstAmount(response.sgst());
        line.setIgstAmount(response.igst());
        line.setCessAmount(BigDecimal.ZERO);
        line.setLineTotal(response.lineTotal());
    }

    public void applySnapshot(LineTaxAmounts line, TaxResult result) {
        line.setTaxableAmount(result.taxableAmount());
        line.setCgstAmount(result.cgstAmount());
        line.setSgstAmount(result.sgstAmount());
        line.setIgstAmount(result.igstAmount());
        line.setCessAmount(result.cessAmount());
        line.setLineTotal(result.lineTotal());
    }

    public void applyTaxSnapshots(LineTaxSnapshots line, TaxResult result) {
        applySnapshot(line, result);
        if (result.taxCategoryId() != null) {
            line.setTaxCategoryId(result.taxCategoryId());
        }
        if (result.taxCategoryCode() != null) {
            line.setTaxCategoryCode(result.taxCategoryCode());
        }
        if (result.taxRuleId() != null) {
            line.setTaxRuleId(result.taxRuleId());
        }
        if (result.taxRuleVersion() != null) {
            line.setTaxRuleVersion(result.taxRuleVersion());
        }
    }

    public void applyPosSnapshot(PosLineTaxSnapshots line, TaxResult result) {
        line.setLineTotal(result.lineTotal());
        if (result.taxCategoryId() != null) {
            line.setTaxCategoryId(result.taxCategoryId());
        }
        if (result.taxCategoryCode() != null) {
            line.setTaxCategoryCode(result.taxCategoryCode());
        }
        if (result.taxRuleId() != null) {
            line.setTaxRuleId(result.taxRuleId());
        }
        if (result.taxRuleVersion() != null) {
            line.setTaxRuleVersion(result.taxRuleVersion());
        }
    }

    public Response toGstResponse(TaxResult result) {
        return new Response(
                result.taxableAmount(),
                result.cgstAmount(),
                result.sgstAmount(),
                result.igstAmount(),
                result.otherTaxAmount(),
                result.lineTotal());
    }

    private static TaxCalculationRequest toEngineRequest(DocumentLineInput input) {
        return new TaxCalculationRequest(
                input.organizationStateCode(),
                input.placeOfSupplyStateCode(),
                input.countryCode() == null ? "IN" : input.countryCode(),
                input.effectiveDate(),
                input.productId(),
                input.productCategoryId(),
                input.taxCategoryId(),
                input.quantity(),
                input.rate(),
                input.discount(),
                input.taxInclusive(),
                input.taxType(),
                input.splitStrategy(),
                input.cgstSharePercent(),
                input.sgstSharePercent());
    }

    private static Request toLegacyRequest(DocumentLineInput input) {
        return new Request(
                input.organizationStateCode(),
                input.placeOfSupplyStateCode(),
                input.taxRate() == null ? BigDecimal.ZERO : input.taxRate(),
                Boolean.TRUE.equals(input.taxInclusive()),
                input.quantity(),
                input.rate(),
                input.discount(),
                input.taxType(),
                input.splitStrategy(),
                input.cgstSharePercent(),
                input.sgstSharePercent());
    }

    private static com.flowledger.tax.entity.TaxRule legacyRule(Request request) {
        com.flowledger.tax.entity.TaxRule rule = new com.flowledger.tax.entity.TaxRule();
        rule.setId(UUID.randomUUID());
        rule.setTaxType(TaxType.from(request.taxType()));
        SplitStrategy strategy = SplitStrategy.from(request.splitStrategy());
        if (strategy == null) {
            strategy = SplitStrategy.defaultFor(rule.getTaxType());
        }
        BigDecimal rate = request.taxRate() == null ? BigDecimal.ZERO : request.taxRate();
        switch (strategy) {
            case NO_SPLIT_IGST -> {
                rule.setIgstRate(rate);
                rule.setCgstRate(BigDecimal.ZERO);
                rule.setSgstRate(BigDecimal.ZERO);
            }
            case NO_SPLIT_OTHER -> {
                rule.setIgstRate(rate);
                rule.setCgstRate(BigDecimal.ZERO);
                rule.setSgstRate(BigDecimal.ZERO);
            }
            default -> {
                BigDecimal cgstShare =
                        request.cgstSharePercent() == null ? new BigDecimal("50") : request.cgstSharePercent();
                BigDecimal sgstShare =
                        request.sgstSharePercent() == null ? new BigDecimal("50") : request.sgstSharePercent();
                rule.setCgstRate(
                        rate.multiply(cgstShare).divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP));
                rule.setSgstRate(
                        rate.multiply(sgstShare).divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP));
                rule.setIgstRate(rate);
            }
        }
        rule.setCessRate(BigDecimal.ZERO);
        rule.setInclusive(Boolean.TRUE.equals(request.taxInclusive()));
        rule.setVersionLabel("legacy-inline");
        return rule;
    }

    public record DocumentLineInput(
            String organizationStateCode,
            String placeOfSupplyStateCode,
            String countryCode,
            LocalDate effectiveDate,
            UUID productId,
            UUID productCategoryId,
            UUID taxCategoryId,
            BigDecimal quantity,
            BigDecimal rate,
            BigDecimal discount,
            Boolean taxInclusive,
            BigDecimal taxRate,
            String taxType,
            String splitStrategy,
            BigDecimal cgstSharePercent,
            BigDecimal sgstSharePercent) {}

    public interface LineTaxAmounts {
        void setTaxableAmount(BigDecimal amount);

        void setCgstAmount(BigDecimal amount);

        void setSgstAmount(BigDecimal amount);

        void setIgstAmount(BigDecimal amount);

        void setCessAmount(BigDecimal amount);

        void setLineTotal(BigDecimal amount);
    }

    public interface LineTaxSnapshots extends LineTaxAmounts {
        void setTaxCategoryId(UUID id);

        void setTaxCategoryCode(String code);

        void setTaxRuleId(UUID id);

        void setTaxRuleVersion(String version);
    }

    public interface PosLineTaxSnapshots {
        void setLineTotal(BigDecimal amount);

        void setTaxCategoryId(UUID id);

        void setTaxCategoryCode(String code);

        void setTaxRuleId(UUID id);

        void setTaxRuleVersion(String version);
    }
}
