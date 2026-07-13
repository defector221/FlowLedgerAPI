package com.flowledger.organization.mapper;
import com.flowledger.organization.dto.*;import com.flowledger.organization.entity.*;import org.mapstruct.*;
@Mapper(componentModel="spring") public interface OrganizationMapper {@Mapping(target="id",ignore=true) void update(UpdateOrganizationRequest r,@MappingTarget Organization o); OrganizationResponse toResponse(Organization o);OrganizationSettingsResponse toResponse(OrganizationSettings s);}
