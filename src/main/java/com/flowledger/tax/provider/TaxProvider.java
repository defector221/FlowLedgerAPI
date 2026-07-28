package com.flowledger.tax.provider;

import com.flowledger.tax.domain.TaxProviderCode;
import com.flowledger.tax.dto.TaxCalculationDtos.TaxResult;

public interface TaxProvider {
    TaxProviderCode code();

    TaxResult calculate(TaxCalculationContext context);
}
