package com.flowledger.migration.mapping;

import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.migration.dto.MigrationDtos.MappingProfileRequest;
import com.flowledger.migration.dto.MigrationDtos.MappingProfileResponse;
import com.flowledger.migration.entity.ImportMappingProfile;
import com.flowledger.migration.repository.ImportMappingProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MappingProfileService {
    private final ImportMappingProfileRepository repo;
    private final MappingEngine mappingEngine;

    public MappingProfileService(ImportMappingProfileRepository repo, MappingEngine mappingEngine) {
        this.repo = repo;
        this.mappingEngine = mappingEngine;
    }

    @Transactional(readOnly = true)
    public List<MappingProfileResponse> list(String module) {
        UUID org = TenantContext.getOrganizationId();
        List<ImportMappingProfile> profiles = module == null || module.isBlank()
                ? repo.findByOrganizationIdAndActiveTrueOrderByNameAsc(org)
                : repo.findByOrganizationIdAndModuleAndActiveTrueOrderByNameAsc(org, module);
        return profiles.stream().map(this::toResponse).toList();
    }

    @Transactional
    public MappingProfileResponse create(MappingProfileRequest request) {
        ImportMappingProfile p = new ImportMappingProfile();
        p.setOrganizationId(TenantContext.getOrganizationId());
        p.setName(request.name());
        p.setModule(request.module().name());
        p.setSourceLabel(request.sourceLabel());
        p.setAutoCreateMissing(Boolean.TRUE.equals(request.autoCreateMissing()));
        p.setMappingsJson(mappingEngine.toJson(request.mappings() == null ? List.of() : request.mappings()));
        TenantContext.userId().ifPresent(p::setCreatedBy);
        return toResponse(repo.save(p));
    }

    @Transactional(readOnly = true)
    public MappingProfileResponse get(UUID id) {
        return toResponse(require(id));
    }

    private ImportMappingProfile require(UUID id) {
        return repo.findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Mapping profile not found"));
    }

    private MappingProfileResponse toResponse(ImportMappingProfile p) {
        return new MappingProfileResponse(
                p.getId(),
                p.getName(),
                p.getModule(),
                p.getSourceLabel(),
                p.isAutoCreateMissing(),
                mappingEngine.fromJson(p.getMappingsJson()),
                p.isActive(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
