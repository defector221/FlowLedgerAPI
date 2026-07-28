package com.flowledger.retail.service;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.location.service.LocationHierarchyService;
import com.flowledger.location.service.LocationScopeService;
import com.flowledger.organization.repository.BranchRepository;
import com.flowledger.retail.entity.RetailCashCounter;
import com.flowledger.retail.entity.RetailCashier;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.entity.RetailStoreType;
import com.flowledger.retail.entity.RetailTerminal;
import com.flowledger.retail.repository.RetailCashCounterRepository;
import com.flowledger.retail.repository.RetailCashierRepository;
import com.flowledger.retail.repository.RetailStoreRepository;
import com.flowledger.retail.repository.RetailStoreTypeRepository;
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
public class RetailStoreService {
    private final RetailModuleGuard guard;
    private final RetailStoreTypeRepository storeTypes;
    private final RetailStoreRepository stores;
    private final RetailCashCounterRepository counters;
    private final RetailTerminalRepository terminals;
    private final RetailCashierRepository cashiers;
    private final BranchRepository branches;
    private final LocationHierarchyService hierarchy;
    private final LocationScopeService scope;

    public RetailStoreService(
            RetailModuleGuard guard,
            RetailStoreTypeRepository storeTypes,
            RetailStoreRepository stores,
            RetailCashCounterRepository counters,
            RetailTerminalRepository terminals,
            RetailCashierRepository cashiers,
            BranchRepository branches,
            LocationHierarchyService hierarchy,
            LocationScopeService scope) {
        this.guard = guard;
        this.storeTypes = storeTypes;
        this.stores = stores;
        this.counters = counters;
        this.terminals = terminals;
        this.cashiers = cashiers;
        this.branches = branches;
        this.hierarchy = hierarchy;
        this.scope = scope;
    }

    // ------------------------------------------------------------- Store types
    @Transactional(readOnly = true)
    public List<StoreTypeResponse> listStoreTypes() {
        return storeTypes.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org()).stream()
                .map(this::map)
                .toList();
    }

    public StoreTypeResponse createStoreType(StoreTypeRequest r) {
        String code = code(r.code());
        if (storeTypes.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), code)) {
            conflict("Store type code already exists");
        }
        RetailStoreType e = new RetailStoreType();
        e.setOrganizationId(org());
        e.setCode(code);
        e.setName(r.name());
        audit(e, true);
        return map(storeTypes.save(e));
    }

    public StoreTypeResponse updateStoreType(UUID id, StoreTypeRequest r) {
        RetailStoreType e = storeTypes
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Store type not found"));
        e.setName(r.name());
        audit(e, false);
        return map(storeTypes.save(e));
    }

    public void deleteStoreType(UUID id) {
        RetailStoreType e = storeTypes
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Store type not found"));
        e.setDeleted(true);
        audit(e, false);
    }

    // ------------------------------------------------------------------ Stores
    @Transactional(readOnly = true)
    public List<StoreResponse> listStores() {
        return listStores(null);
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> listStores(UUID branchId) {
        List<RetailStore> rows = branchId != null
                ? stores.findByOrganizationIdAndBranchIdAndDeletedFalseOrderByNameAsc(org(), branchId)
                : stores.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org());
        if (!scope.isOrgWideAdmin()) {
            var allowed = scope.accessibleStoreIds();
            if (!allowed.isEmpty()) {
                rows = rows.stream().filter(s -> allowed.contains(s.getId())).toList();
            }
        }
        return rows.stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public StoreResponse getStore(UUID id) {
        return map(loadStore(id));
    }

    public StoreResponse createStore(StoreRequest r) {
        String code = code(r.code());
        if (stores.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), code)) {
            conflict("Store code already exists");
        }
        validateBranch(r.branchId());
        RetailStore e = new RetailStore();
        e.setOrganizationId(org());
        e.setCode(code);
        applyStore(e, r);
        audit(e, true);
        return map(stores.save(e));
    }

    public StoreResponse updateStore(UUID id, StoreRequest r) {
        RetailStore e = loadStore(id);
        applyStore(e, r);
        audit(e, false);
        return map(stores.save(e));
    }

    public void deleteStore(UUID id) {
        RetailStore e = loadStore(id);
        e.setDeleted(true);
        e.setStatus("INACTIVE");
        audit(e, false);
    }

    private void applyStore(RetailStore e, StoreRequest r) {
        e.setName(r.name());
        e.setBranchId(r.branchId() != null ? r.branchId() : hierarchy.defaultBranchId());
        e.setStoreTypeId(r.storeTypeId());
        if (r.storeType() != null) {
            e.setStoreType(r.storeType());
        }
        e.setWarehouseId(r.warehouseId());
        e.setManagerId(r.managerId());
        e.setAddress(r.address());
        e.setCity(r.city());
        e.setState(r.state());
        e.setPostalCode(r.postalCode());
        e.setCountry(r.country() == null || r.country().isBlank() ? "IN" : r.country());
        e.setPhone(r.phone());
        e.setEmail(r.email());
        e.setDefaultCurrency(r.defaultCurrency());
        e.setTimezone(r.timezone());
        if (r.allowNegativeStock() != null) e.setAllowNegativeStock(r.allowNegativeStock());
        if (r.allowOfflinePos() != null) e.setAllowOfflinePos(r.allowOfflinePos());
        if (r.enableClickAndCollect() != null) e.setEnableClickAndCollect(r.enableClickAndCollect());
        if (r.enableLoyalty() != null) e.setEnableLoyalty(r.enableLoyalty());
        if (r.enableGiftCard() != null) e.setEnableGiftCard(r.enableGiftCard());
        e.setStatus(r.status() == null ? "ACTIVE" : r.status());
    }

    private void validateBranch(UUID branchId) {
        UUID effective = branchId != null ? branchId : hierarchy.defaultBranchId();
        if (effective == null) {
            conflict("Branch is required");
        }
        branches.findByIdAndOrganizationId(effective, org()).orElseThrow(() -> notFound("Branch not found"));
    }

    private RetailStore loadStore(UUID id) {
        return stores.findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Store not found"));
    }

    // ---------------------------------------------------------------- Counters
    @Transactional(readOnly = true)
    public List<CounterResponse> listCounters(UUID storeId) {
        return counters.findByOrganizationIdAndStoreIdAndDeletedFalseOrderByNameAsc(org(), storeId).stream()
                .map(this::map)
                .toList();
    }

    public CounterResponse createCounter(CounterRequest r) {
        String code = code(r.code());
        if (counters.existsByOrganizationIdAndStoreIdAndCodeIgnoreCaseAndDeletedFalse(org(), r.storeId(), code)) {
            conflict("Counter code already exists in store");
        }
        RetailCashCounter e = new RetailCashCounter();
        e.setOrganizationId(org());
        e.setStoreId(r.storeId());
        e.setCode(code);
        e.setName(r.name());
        e.setStatus(r.status() == null ? "ACTIVE" : r.status());
        audit(e, true);
        return map(counters.save(e));
    }

    public CounterResponse updateCounter(UUID id, CounterRequest r) {
        RetailCashCounter e = counters.findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Counter not found"));
        e.setName(r.name());
        if (r.status() != null) {
            e.setStatus(r.status());
        }
        audit(e, false);
        return map(counters.save(e));
    }

    public void deleteCounter(UUID id) {
        RetailCashCounter e = counters.findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Counter not found"));
        e.setDeleted(true);
        audit(e, false);
    }

    // --------------------------------------------------------------- Terminals
    @Transactional(readOnly = true)
    public List<TerminalResponse> listTerminals(UUID storeId) {
        return terminals.findByOrganizationIdAndStoreIdAndDeletedFalseOrderByNameAsc(org(), storeId).stream()
                .map(this::map)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TerminalResponse> listAllTerminals(UUID storeId) {
        if (storeId != null) {
            return listTerminals(storeId);
        }
        return stores.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org()).stream()
                .flatMap(s -> listTerminals(s.getId()).stream())
                .toList();
    }

    @Transactional(readOnly = true)
    public TerminalResponse getTerminal(UUID id) {
        RetailTerminal e = terminals
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Terminal not found"));
        return map(e);
    }

    public TerminalResponse createTerminal(TerminalRequest r) {
        String code = code(r.code());
        if (terminals.existsByOrganizationIdAndStoreIdAndCodeIgnoreCaseAndDeletedFalse(org(), r.storeId(), code)) {
            conflict("Terminal code already exists in store");
        }
        RetailTerminal e = new RetailTerminal();
        e.setOrganizationId(org());
        e.setStoreId(r.storeId());
        e.setCounterId(r.counterId());
        e.setCode(code);
        e.setName(r.name());
        e.setDeviceId(r.deviceId());
        e.setStatus(r.status() == null ? "ACTIVE" : r.status());
        audit(e, true);
        return map(terminals.save(e));
    }

    public TerminalResponse updateTerminal(UUID id, TerminalRequest r) {
        RetailTerminal e = terminals
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Terminal not found"));
        e.setCounterId(r.counterId());
        e.setName(r.name());
        if (r.deviceId() != null) {
            e.setDeviceId(r.deviceId());
        }
        if (r.status() != null) {
            e.setStatus(r.status());
        }
        audit(e, false);
        return map(terminals.save(e));
    }

    public void deleteTerminal(UUID id) {
        RetailTerminal e = terminals
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Terminal not found"));
        e.setDeleted(true);
        audit(e, false);
    }

    // ---------------------------------------------------------------- Cashiers
    @Transactional(readOnly = true)
    public List<CashierResponse> listCashiers(UUID storeId) {
        return cashiers.findByOrganizationIdAndStoreIdAndDeletedFalseOrderByDisplayNameAsc(org(), storeId).stream()
                .map(this::map)
                .toList();
    }

    public CashierResponse createCashier(CashierRequest r) {
        if (cashiers.existsByOrganizationIdAndStoreIdAndUserIdAndDeletedFalse(org(), r.storeId(), r.userId())) {
            conflict("Cashier already exists for user in store");
        }
        RetailCashier e = new RetailCashier();
        e.setOrganizationId(org());
        e.setStoreId(r.storeId());
        e.setUserId(r.userId());
        e.setEmployeeCode(r.employeeCode());
        e.setDisplayName(r.displayName());
        e.setStatus(r.status() == null ? "ACTIVE" : r.status());
        audit(e, true);
        return map(cashiers.save(e));
    }

    public CashierResponse updateCashier(UUID id, CashierRequest r) {
        RetailCashier e = cashiers.findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Cashier not found"));
        e.setEmployeeCode(r.employeeCode());
        e.setDisplayName(r.displayName());
        if (r.status() != null) {
            e.setStatus(r.status());
        }
        audit(e, false);
        return map(cashiers.save(e));
    }

    public void deleteCashier(UUID id) {
        RetailCashier e = cashiers.findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Cashier not found"));
        e.setDeleted(true);
        audit(e, false);
    }

    // ----------------------------------------------------------------- Mappers
    private StoreTypeResponse map(RetailStoreType e) {
        return new StoreTypeResponse(e.getId(), e.getCode(), e.getName(), e.getVersion());
    }

    private StoreResponse map(RetailStore e) {
        return new StoreResponse(
                e.getId(),
                e.getCode(),
                e.getName(),
                e.getBranchId(),
                e.getStoreTypeId(),
                e.getStoreType(),
                e.getWarehouseId(),
                e.getManagerId(),
                e.getAddress(),
                e.getCity(),
                e.getState(),
                e.getPostalCode(),
                e.getCountry(),
                e.getPhone(),
                e.getEmail(),
                e.getStatus(),
                e.getDefaultCurrency(),
                e.getTimezone(),
                e.isAllowNegativeStock(),
                e.isAllowOfflinePos(),
                e.isEnableClickAndCollect(),
                e.isEnableLoyalty(),
                e.isEnableGiftCard(),
                e.getVersion());
    }

    private CounterResponse map(RetailCashCounter e) {
        return new CounterResponse(e.getId(), e.getStoreId(), e.getCode(), e.getName(), e.getStatus(), e.getVersion());
    }

    private TerminalResponse map(RetailTerminal e) {
        return new TerminalResponse(
                e.getId(),
                e.getStoreId(),
                e.getCounterId(),
                e.getCode(),
                e.getName(),
                e.getDeviceId(),
                e.getStatus(),
                e.getVersion());
    }

    private CashierResponse map(RetailCashier e) {
        return new CashierResponse(
                e.getId(),
                e.getStoreId(),
                e.getUserId(),
                e.getEmployeeCode(),
                e.getDisplayName(),
                e.getStatus(),
                e.getVersion());
    }

    // ----------------------------------------------------------------- Helpers
    private UUID org() {
        return guard.ensureEnabled();
    }

    private String code(String provided) {
        return provided.trim().toUpperCase(Locale.ROOT);
    }

    private void audit(Object entity, boolean created) {
        TenantContext.userId().ifPresent(u -> {
            if (entity instanceof com.flowledger.common.entity.AuditedEntity a) {
                if (created) {
                    a.setCreatedBy(u);
                }
                a.setUpdatedBy(u);
            }
        });
    }

    private ResponseStatusException notFound(String m) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, m);
    }

    private void conflict(String m) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, m);
    }
}
