package com.flowledger.location.service;

import static com.flowledger.location.dto.LocationDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.location.entity.CashDrawer;
import com.flowledger.location.repository.CashDrawerRepository;
import com.flowledger.retail.domain.RetailEnums.ShiftStatus;
import com.flowledger.retail.entity.RetailShift;
import com.flowledger.retail.repository.RetailShiftRepository;
import com.flowledger.retail.repository.RetailTerminalRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CashDrawerService {
    private final CashDrawerRepository drawers;
    private final RetailTerminalRepository terminals;
    private final RetailShiftRepository shifts;
    private final LocationScopeService scope;

    public CashDrawerService(
            CashDrawerRepository drawers,
            RetailTerminalRepository terminals,
            RetailShiftRepository shifts,
            LocationScopeService scope) {
        this.drawers = drawers;
        this.terminals = terminals;
        this.shifts = shifts;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public List<CashDrawerResponse> list(UUID terminalId) {
        List<CashDrawer> rows = terminalId != null
                ? drawers.findByOrganizationIdAndTerminalIdAndDeletedFalseOrderByDrawerNameAsc(org(), terminalId)
                : drawers.findByOrganizationIdAndDeletedFalseOrderByDrawerNameAsc(org());
        return rows.stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public CashDrawerResponse get(UUID id) {
        return map(load(id));
    }

    public CashDrawerResponse create(CashDrawerRequest request) {
        terminals.findByIdAndOrganizationIdAndDeletedFalse(request.terminalId(), org())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Terminal not found"));
        String code = request.drawerCode().trim().toUpperCase(Locale.ROOT);
        if (drawers.existsByOrganizationIdAndTerminalIdAndDrawerCodeIgnoreCaseAndDeletedFalse(
                org(), request.terminalId(), code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Drawer code already exists on terminal");
        }
        CashDrawer drawer = new CashDrawer();
        drawer.setOrganizationId(org());
        drawer.setTerminalId(request.terminalId());
        drawer.setDrawerCode(code);
        drawer.setDrawerName(request.drawerName().trim());
        drawer.setStatus(request.status() == null ? "ACTIVE" : request.status());
        TenantContext.userId().ifPresent(u -> {
            drawer.setCreatedBy(u);
            drawer.setUpdatedBy(u);
        });
        return map(drawers.save(drawer));
    }

    public CashDrawerResponse update(UUID id, CashDrawerRequest request) {
        CashDrawer drawer = load(id);
        drawer.setDrawerName(request.drawerName().trim());
        if (request.status() != null) {
            drawer.setStatus(request.status());
        }
        TenantContext.userId().ifPresent(drawer::setUpdatedBy);
        return map(drawers.save(drawer));
    }

    public void delete(UUID id) {
        CashDrawer drawer = load(id);
        drawer.setDeleted(true);
        drawer.setStatus("INACTIVE");
        TenantContext.userId().ifPresent(drawer::setUpdatedBy);
    }

    private CashDrawer load(UUID id) {
        return drawers.findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cash drawer not found"));
    }

    private CashDrawerResponse map(CashDrawer drawer) {
        RetailShift openShift = shifts
                .findFirstByOrganizationIdAndDrawerIdAndStatusOrderByOpenedAtDesc(org(), drawer.getId(), ShiftStatus.OPEN)
                .orElse(null);
        return new CashDrawerResponse(
                drawer.getId(),
                drawer.getTerminalId(),
                drawer.getDrawerCode(),
                drawer.getDrawerName(),
                drawer.getStatus(),
                openShift != null ? openShift.getCashierId() : null,
                null,
                openShift != null ? openShift.getOpeningFloat() : null,
                openShift != null ? openShift.getStatus().name() : null);
    }

    private UUID org() {
        return TenantContext.getOrganizationId();
    }
}
