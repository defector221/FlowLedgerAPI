package com.flowledger.finance.voucher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.finance.voucher.domain.VoucherStatus;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.finance.voucher.dto.VoucherDtos.VoucherLineRequest;
import com.flowledger.finance.voucher.dto.VoucherDtos.VoucherRequest;
import com.flowledger.finance.voucher.entity.Voucher;
import com.flowledger.finance.voucher.entity.VoucherLine;
import com.flowledger.finance.voucher.repository.VoucherRepository;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.platform.history.service.DocumentHistoryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherServiceLifecycleTest {
    @Mock
    VoucherRepository vouchers;

    @Mock
    VoucherSequenceService sequences;

    @Mock
    OrganizationRepository organizations;

    @Mock
    DocumentHistoryService history;

    VoucherService service;
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID a1 = UUID.randomUUID();
    UUID a2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(orgId, userId);
        service = new VoucherService(vouchers, sequences, new VoucherValidator(), organizations, history);
        Organization org = new Organization();
        org.setId(orgId);
        org.setFinancialYearStart("04-01");
        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(sequences.nextNumber(eq(orgId), any(), any(), any(), any())).thenReturn("JV/2026-27/0001");
        when(vouchers.save(any(Voucher.class))).thenAnswer(inv -> {
            Voucher v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(UUID.randomUUID());
            }
            return v;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createDraftPersistsBalancedVoucher() {
        var response = service.createDraft(balancedRequest());
        assertEquals(VoucherStatus.DRAFT, response.status());
        assertEquals("JV/2026-27/0001", response.voucherNumber());
        verify(history).record(eq("VOUCHER"), any(), eq("CREATED"), any(), any());
    }

    @Test
    void cannotUpdatePostedVoucher() {
        Voucher posted = draftEntity();
        posted.setStatus(VoucherStatus.POSTED);
        posted.setPosted(true);
        when(vouchers.findByIdAndOrganizationIdAndDeletedAtIsNull(posted.getId(), orgId))
                .thenReturn(Optional.of(posted));
        assertThrows(ConflictException.class, () -> service.updateDraft(posted.getId(), balancedRequest()));
    }

    @Test
    void approveMovesDraftToApproved() {
        Voucher draft = draftEntity();
        VoucherLine l1 = new VoucherLine();
        l1.setAccountId(a1);
        l1.setDebit(new BigDecimal("100.00"));
        l1.setCredit(BigDecimal.ZERO);
        l1.setOrganizationId(orgId);
        l1.setVoucher(draft);
        VoucherLine l2 = new VoucherLine();
        l2.setAccountId(a2);
        l2.setDebit(BigDecimal.ZERO);
        l2.setCredit(new BigDecimal("100.00"));
        l2.setOrganizationId(orgId);
        l2.setVoucher(draft);
        draft.getLines().add(l1);
        draft.getLines().add(l2);
        when(vouchers.findByIdAndOrganizationIdAndDeletedAtIsNull(draft.getId(), orgId))
                .thenReturn(Optional.of(draft));
        var response = service.approve(draft.getId());
        assertEquals(VoucherStatus.APPROVED, response.status());
        ArgumentCaptor<Voucher> captor = ArgumentCaptor.forClass(Voucher.class);
        verify(vouchers).save(captor.capture());
        assertEquals(VoucherStatus.APPROVED, captor.getValue().getStatus());
    }

    private VoucherRequest balancedRequest() {
        return new VoucherRequest(
                null,
                VoucherType.JOURNAL,
                LocalDate.of(2026, 7, 1),
                "INR",
                BigDecimal.ONE,
                null,
                null,
                "Test",
                false,
                null,
                null,
                List.of(
                        new VoucherLineRequest(
                                a1,
                                new BigDecimal("100.00"),
                                BigDecimal.ZERO,
                                "Dr",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                1),
                        new VoucherLineRequest(
                                a2,
                                BigDecimal.ZERO,
                                new BigDecimal("100.00"),
                                "Cr",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                2)));
    }

    private Voucher draftEntity() {
        Voucher v = new Voucher();
        v.setId(UUID.randomUUID());
        v.setOrganizationId(orgId);
        v.setVoucherNumber("JV/2026-27/0001");
        v.setVoucherType(VoucherType.JOURNAL);
        v.setVoucherDate(LocalDate.of(2026, 7, 1));
        v.setStatus(VoucherStatus.DRAFT);
        v.setLines(new ArrayList<>());
        return v;
    }
}
