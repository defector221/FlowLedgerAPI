package com.flowledger.customer.mapper;
import com.flowledger.customer.entity.Customer; import com.flowledger.customer.dto.CustomerDtos.*; import org.mapstruct.*;
@Mapper(componentModel="spring",nullValuePropertyMappingStrategy=NullValuePropertyMappingStrategy.IGNORE) public interface CustomerMapper { Customer toEntity(Create dto); Response toResponse(Customer entity); @BeanMapping(nullValuePropertyMappingStrategy=NullValuePropertyMappingStrategy.IGNORE) void update(Update dto,@MappingTarget Customer entity); }
