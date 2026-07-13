package com.flowledger.warehouse.mapper;

import com.flowledger.warehouse.dto.WarehouseDtos.*;
import com.flowledger.warehouse.entity.Warehouse;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface WarehouseMapper {
    Warehouse toEntity(Create dto);

    Response toResponse(Warehouse entity);

    void update(Update dto, @MappingTarget Warehouse entity);
}
