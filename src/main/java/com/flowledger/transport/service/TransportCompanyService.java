package com.flowledger.transport.service;

import static com.flowledger.transport.dto.TransportDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.transport.entity.TransportCompany;
import com.flowledger.transport.repository.TransportCompanyRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TransportCompanyService {
    private final TransportCompanyRepository repository;

    public TransportCompanyService(TransportCompanyRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public List<CompanyResponse> list() { return repository.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org()).stream().map(this::map).toList(); }
    @Transactional(readOnly = true)
    public CompanyResponse get(UUID id) { return map(load(id)); }

    public CompanyResponse create(CompanyRequest r) {
        if (repository.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), r.code())) conflict("Company code already exists");
        TransportCompany e = new TransportCompany();
        e.setOrganizationId(org());
        apply(e, r);
        audit(e, true);
        return map(repository.save(e));
    }

    public CompanyResponse update(UUID id, CompanyRequest r) {
        TransportCompany e = load(id);
        if (!e.getCode().equalsIgnoreCase(r.code()) && repository.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), r.code())) conflict("Company code already exists");
        apply(e, r);
        audit(e, false);
        return map(repository.save(e));
    }

    public void delete(UUID id) { TransportCompany e = load(id); e.setDeleted(true); e.setStatus("INACTIVE"); audit(e, false); }

    private void apply(TransportCompany e, CompanyRequest r) {
        e.setName(r.name()); e.setCode(r.code()); e.setGstin(r.gstin()); e.setPan(r.pan()); e.setEmail(r.email());
        e.setPhone(r.phone()); e.setAddress(r.address()); e.setCity(r.city()); e.setState(r.state());
        e.setStateCode(r.stateCode()); e.setCountry(r.country() == null ? "India" : r.country());
        e.setStatus(r.status() == null ? "ACTIVE" : r.status()); e.setNotes(r.notes());
    }
    private TransportCompany load(UUID id) { return repository.findByIdAndOrganizationIdAndDeletedFalse(id, org()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transport company not found")); }
    private CompanyResponse map(TransportCompany e) { return new CompanyResponse(e.getId(), e.getName(), e.getCode(), e.getGstin(), e.getPan(), e.getEmail(), e.getPhone(), e.getAddress(), e.getCity(), e.getState(), e.getStateCode(), e.getCountry(), e.getStatus(), e.getNotes(), e.getVersion()); }
    private UUID org() { return TenantContext.getOrganizationId(); }
    private void audit(TransportCompany e, boolean created) { TenantContext.userId().ifPresent(u -> { if (created) e.setCreatedBy(u); e.setUpdatedBy(u); }); }
    private void conflict(String m) { throw new ResponseStatusException(HttpStatus.CONFLICT, m); }
}
