package com.flowledger.retail.service;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.retail.domain.RetailEnums.PaymentMode;
import com.flowledger.retail.domain.RetailEnums.PosSaleStatus;
import com.flowledger.retail.domain.RetailEnums.ShiftStatus;
import com.flowledger.retail.entity.PosSale;
import com.flowledger.retail.entity.PosSalePayment;
import com.flowledger.retail.entity.RetailShift;
import com.flowledger.retail.repository.PosSalePaymentRepository;
import com.flowledger.retail.repository.PosSaleRepository;
import com.flowledger.retail.repository.RetailShiftRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RetailShiftService {
    private final RetailModuleGuard guard;
    private final RetailShiftRepository shifts;
    private final PosSaleRepository posSales;
    private final PosSalePaymentRepository posPayments;

    public RetailShiftService(
            RetailModuleGuard guard,
            RetailShiftRepository shifts,
            PosSaleRepository posSales,
            PosSalePaymentRepository posPayments) {
        this.guard = guard;
        this.shifts = shifts;
        this.posSales = posSales;
        this.posPayments = posPayments;
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> list(UUID storeId) {
        List<RetailShift> found = storeId == null
                ? shifts.findByOrganizationIdAndDeletedFalseOrderByOpenedAtDesc(org())
                : shifts.findByOrganizationIdAndStoreIdAndDeletedFalseOrderByOpenedAtDesc(org(), storeId);
        return found.stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public ShiftResponse get(UUID id) {
        return map(load(id));
    }

    public ShiftResponse open(OpenShiftRequest r) {
        UUID org = org();
        List<RetailShift> existingOpen =
                shifts.findByOrganizationIdAndCashierIdAndStatusAndDeletedFalse(org, r.cashierId(), ShiftStatus.OPEN);
        if (!existingOpen.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cashier already has an open shift");
        }
        RetailShift e = new RetailShift();
        e.setOrganizationId(org);
        e.setStoreId(r.storeId());
        e.setCounterId(r.counterId());
        e.setTerminalId(r.terminalId());
        e.setCashierId(r.cashierId());
        e.setStatus(ShiftStatus.OPEN);
        e.setOpenedAt(OffsetDateTime.now());
        e.setOpeningFloat(r.openingFloat() == null ? BigDecimal.ZERO : r.openingFloat());
        e.setNotes(r.notes());
        audit(e, true);
        return map(shifts.save(e));
    }

    public ShiftResponse close(UUID id, CloseShiftRequest r) {
        RetailShift e = load(id);
        if (e.getStatus() == ShiftStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Shift already closed");
        }
        BigDecimal expected = expectedCash(e);
        e.setStatus(ShiftStatus.CLOSED);
        e.setClosedAt(OffsetDateTime.now());
        e.setClosingCash(r.closingCash());
        e.setExpectedCash(expected);
        e.setVariance(r.closingCash().subtract(expected));
        if (r.notes() != null) {
            e.setNotes(r.notes());
        }
        audit(e, false);
        return map(shifts.save(e));
    }

    private BigDecimal expectedCash(RetailShift shift) {
        BigDecimal cash = shift.getOpeningFloat() == null ? BigDecimal.ZERO : shift.getOpeningFloat();
        List<PosSale> sales = posSales.findByOrganizationIdAndShiftIdAndDeletedFalse(org(), shift.getId());
        for (PosSale sale : sales) {
            if (sale.getStatus() != PosSaleStatus.COMPLETED) {
                continue;
            }
            for (PosSalePayment payment : posPayments.findByOrganizationIdAndPosSaleId(org(), sale.getId())) {
                if (payment.getPaymentMode() == PaymentMode.CASH) {
                    cash = cash.add(payment.getAmount());
                }
            }
        }
        return cash;
    }

    private RetailShift load(UUID id) {
        return shifts.findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
    }

    private ShiftResponse map(RetailShift e) {
        return new ShiftResponse(
                e.getId(),
                e.getStoreId(),
                e.getCounterId(),
                e.getTerminalId(),
                e.getCashierId(),
                e.getStatus(),
                e.getOpenedAt(),
                e.getClosedAt(),
                e.getOpeningFloat(),
                e.getClosingCash(),
                e.getExpectedCash(),
                e.getVariance(),
                e.getNotes(),
                e.getVersion());
    }

    private UUID org() {
        return guard.ensureEnabled();
    }

    private void audit(RetailShift e, boolean created) {
        TenantContext.userId().ifPresent(u -> {
            if (created) {
                e.setCreatedBy(u);
            }
            e.setUpdatedBy(u);
        });
    }
}
