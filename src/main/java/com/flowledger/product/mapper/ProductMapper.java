package com.flowledger.product.mapper;

import com.flowledger.product.dto.ProductDtos.*;
import com.flowledger.product.entity.Product;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProductMapper {
    default Product toEntity(Create d) {
        Product entity = new Product();
        updateFromCreate(d, entity);
        return entity;
    }

    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
            ignoreUnmappedSourceProperties = {"warehouseId", "supplierPrices"})
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateFromCreate(Create d, @MappingTarget Product entity);

    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "unitName", ignore = true)
    @Mapping(target = "taxRateName", ignore = true)
    @Mapping(target = "taxType", ignore = true)
    @Mapping(target = "supplierCount", ignore = true)
    @Mapping(target = "preferredSupplierName", ignore = true)
    Response toResponse(Product e);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void update(Update d, @MappingTarget Product e);
}
