package com.flowledger.transport.service;

import static com.flowledger.transport.dto.TransportDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.EntityCodeGenerator;
import com.flowledger.transport.entity.TransportCompany;
import com.flowledger.transport.repository.TransportCompanyRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TransportCompanyService {
    private final TransportCompanyRepository repository;

    public TransportCompanyService(TransportCompanyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> list() {
        return repository.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org()).stream()
                .map(this::map)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(UUID id) {
        return map(load(id));
    }

    public CompanyResponse create(CompanyRequest r) {
        String code = resolveCode(r.code(), r.name());
        if (repository.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), code)) {
            conflict("Company code already exists");
        }
        TransportCompany company = new TransportCompany();
        company.setOrganizationId(org());
        apply(company, r, code);
        audit(company, true);
        return map(repository.save(company));
    }

    public CompanyResponse update(UUID id, CompanyRequest r) {
        TransportCompany company = load(id);
        String code = r.code() == null || r.code().isBlank()
                ? company.getCode()
                : r.code().trim().toUpperCase(Locale.ROOT);
        if (!company.getCode().equalsIgnoreCase(code)
                && repository.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), code)) {
            conflict("Company code already exists");
        }
        apply(company, r, code);
        audit(company, false);
        return map(repository.save(company));
    }

    public void delete(UUID id) {
        TransportCompany company = load(id);
        company.setDeleted(true);
        company.setStatus("INACTIVE");
        audit(company, false);
    }

    private void apply(TransportCompany company, CompanyRequest r, String code) {
        company.setName(r.name());
        company.setCode(code);
        company.setGstin(r.gstin());
        company.setPan(r.pan());
        company.setEmail(r.email());
        company.setPhone(r.phone());
        company.setAddress(r.address());
        company.setCity(r.city());
        company.setState(r.state());
        company.setStateCode(r.stateCode());
        company.setCountry(r.country() == null ? "India" : r.country());
        company.setStatus(r.status() == null ? "ACTIVE" : r.status());
        company.setNotes(r.notes());
    }

    private String resolveCode(String provided, String name) {
        if (provided != null && !provided.isBlank()) {
            return provided.trim().toUpperCase(Locale.ROOT);
        }
        return EntityCodeGenerator.uniqueFromName(
                name,
                "TRN",
                candidate -> repository.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), candidate));
    }

    private TransportCompany load(UUID id) {
        return repository
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transport company not found"));
    }

    private CompanyResponse map(TransportCompany company) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getCode(),
                company.getGstin(),
                company.getPan(),
                company.getEmail(),
                company.getPhone(),
                company.getAddress(),
                company.getCity(),
                company.getState(),
                company.getStateCode(),
                company.getCountry(),
                company.getStatus(),
                company.getNotes(),
                company.getVersion());
    }

    private UUID org() {
        return TenantContext.getOrganizationId();
    }

    private void audit(TransportCompany company, boolean created) {
        TenantContext.userId().ifPresent(u -> {
            if (created) company.setCreatedBy(u);
            company.setUpdatedBy(u);
        });
    }

    private void conflict(String m) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, m);
    }
}
