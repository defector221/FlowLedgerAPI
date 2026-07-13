package com.flowledger.supplier.mapper;

import com.flowledger.supplier.dto.SupplierDtos.*;
import com.flowledger.supplier.entity.Supplier;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SupplierMapper {
    default Supplier toEntity(Create dto) {
        Supplier entity = new Supplier();
        updateFromCreate(dto, entity);
        return entity;
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromCreate(Create dto, @MappingTarget Supplier entity);

    Response toResponse(Supplier entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void update(Update dto, @MappingTarget Supplier entity);
}
