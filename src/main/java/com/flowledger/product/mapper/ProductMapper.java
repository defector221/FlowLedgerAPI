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

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromCreate(Create d, @MappingTarget Product entity);

    Response toResponse(Product e);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void update(Update d, @MappingTarget Product e);
}
