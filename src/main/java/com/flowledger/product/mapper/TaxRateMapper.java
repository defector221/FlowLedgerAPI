package com.flowledger.product.mapper;

import com.flowledger.product.dto.TaxRateDtos.*;
import com.flowledger.product.entity.TaxRate;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TaxRateMapper {
    TaxRate toEntity(Create d);

    Response toResponse(TaxRate e);

    void update(Update d, @MappingTarget TaxRate e);
}
