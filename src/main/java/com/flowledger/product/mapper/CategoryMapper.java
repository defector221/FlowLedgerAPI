package com.flowledger.product.mapper;

import com.flowledger.product.dto.CategoryDtos.*;
import com.flowledger.product.entity.Category;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CategoryMapper {
    Category toEntity(Create d);

    Response toResponse(Category e);

    void update(Update d, @MappingTarget Category e);
}
