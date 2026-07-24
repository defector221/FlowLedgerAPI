package com.flowledger.finance.voucher.service;

import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.finance.voucher.domain.VoucherStatus;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.finance.voucher.dto.VoucherDtos.*;
import com.flowledger.finance.voucher.entity.Voucher;
import com.flowledger.finance.voucher.entity.VoucherLine;
import com.flowledger.finance.voucher.repository.VoucherRepository;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.platform.history.service.DocumentHistoryService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherService {
    private static final String ENTITY_TYPE = "VOUCHER";

    private final VoucherRepository vouchers;
    private final VoucherSequenceService sequences;
    private final VoucherValidator validator;
    private final OrganizationRepository organizations;
    private final DocumentHistoryService history;

    public VoucherService(
            VoucherRepository vouchers,
            VoucherSequenceService sequences,
            VoucherValidator validator,
            OrganizationRepository organizations,
            DocumentHistoryService history) {
        this.vouchers = vouchers;
        this.sequences = sequences;
        this.validator = validator;
        this.organizations = organizations;
        this.history = history;
    }

    @Transactional
    public VoucherResponse createDraft(VoucherRequest request) {
        UUID org = TenantContext.getOrganizationId();
        var totals = validator.validateLines(request.lines());
        Organization organization =
                organizations.findById(org).orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        Voucher voucher = new Voucher();
        voucher.setOrganizationId(org);
        applyHeader(voucher, request);
        voucher.setStatus(VoucherStatus.DRAFT);
        voucher.setVoucherNumber(sequences.nextNumber(
                org,
                request.branchId(),
                request.voucherType(),
                organization.getFinancialYearStart(),
                request.voucherDate()));
        replaceLines(voucher, request.lines());
        recalculateTotals(voucher, totals);
        Voucher saved = vouchers.save(voucher);
        history.record(ENTITY_TYPE, saved.getId(), "CREATED", "Draft voucher created", null);
        return toResponse(saved);
    }

    @Transactional
    public VoucherResponse updateDraft(UUID id, VoucherRequest request) {
        Voucher voucher = load(id);
        if (voucher.getStatus() != VoucherStatus.DRAFT) {
            throw new ConflictException("Only draft vouchers can be updated");
        }
        var totals = validator.validateLines(request.lines());
        applyHeader(voucher, request);
        replaceLines(voucher, request.lines());
        recalculateTotals(voucher, totals);
        Voucher saved = vouchers.save(voucher);
        history.record(ENTITY_TYPE, saved.getId(), "UPDATED", "Draft voucher updated", null);
        return toResponse(saved);
    }

    @Transactional
    public VoucherResponse approve(UUID id) {
        Voucher voucher = load(id);
        if (voucher.getStatus() != VoucherStatus.DRAFT) {
            throw new ConflictException("Only draft vouchers can be approved");
        }
        validator.validateLines(toLineRequests(voucher.getLines()));
        voucher.setStatus(VoucherStatus.APPROVED);
        Voucher saved = vouchers.save(voucher);
        history.record(ENTITY_TYPE, saved.getId(), "APPROVED", "Voucher approved", null);
        return toResponse(saved);
    }

    @Transactional
    public VoucherResponse cancel(UUID id) {
        Voucher voucher = load(id);
        if (voucher.getStatus() == VoucherStatus.POSTED || voucher.getStatus() == VoucherStatus.REVERSED) {
            throw new ConflictException("Posted or reversed vouchers cannot be cancelled");
        }
        if (voucher.getStatus() == VoucherStatus.CANCELLED) {
            return toResponse(voucher);
        }
        voucher.setStatus(VoucherStatus.CANCELLED);
        Voucher saved = vouchers.save(voucher);
        history.record(ENTITY_TYPE, saved.getId(), "CANCELLED", "Voucher cancelled", null);
        return toResponse(saved);
    }

    @Transactional
    public VoucherResponse duplicate(UUID id) {
        Voucher source = load(id);
        Organization organization = organizations
                .findById(source.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        Voucher copy = new Voucher();
        copy.setOrganizationId(source.getOrganizationId());
        copy.setBranchId(source.getBranchId());
        copy.setVoucherType(source.getVoucherType());
        copy.setVoucherDate(LocalDate.now());
        copy.setCurrencyCode(source.getCurrencyCode());
        copy.setExchangeRate(source.getExchangeRate());
        copy.setNarration(source.getNarration());
        copy.setStatus(VoucherStatus.DRAFT);
        copy.setRecurring(false);
        copy.setVoucherNumber(sequences.nextNumber(
                source.getOrganizationId(),
                source.getBranchId(),
                source.getVoucherType(),
                organization.getFinancialYearStart(),
                copy.getVoucherDate()));

        List<VoucherLineRequest> lineRequests = toLineRequests(source.getLines());
        var totals = validator.validateLines(lineRequests);
        replaceLines(copy, lineRequests);
        recalculateTotals(copy, totals);
        Voucher saved = vouchers.save(copy);
        history.record(ENTITY_TYPE, saved.getId(), "DUPLICATED", "Duplicated from " + source.getVoucherNumber(), null);
        return toResponse(saved);
    }

    /**
     * Helper for document adapters (sales/purchase/payment). Creates an APPROVED voucher ready for
     * {@link PostingEngine#post}.
     */
    @Transactional
    public VoucherResponse createFromDocument(
            VoucherType type,
            LocalDate voucherDate,
            UUID branchId,
            String currencyCode,
            BigDecimal exchangeRate,
            String referenceType,
            UUID referenceId,
            String narration,
            List<VoucherLineRequest> lines) {
        UUID org = TenantContext.getOrganizationId();
        vouchers.findByOrganizationIdAndReferenceTypeAndReferenceIdAndDeletedAtIsNull(org, referenceType, referenceId)
                .ifPresent(existing -> {
                    throw new ConflictException("Voucher already exists for " + referenceType + " " + referenceId + ": "
                            + existing.getId());
                });

        VoucherRequest request = new VoucherRequest(
                branchId,
                type,
                voucherDate != null ? voucherDate : LocalDate.now(),
                currencyCode != null ? currencyCode : "INR",
                exchangeRate != null ? exchangeRate : BigDecimal.ONE,
                referenceType,
                referenceId,
                narration,
                false,
                null,
                null,
                lines);
        var totals = validator.validateLines(request.lines());
        Organization organization =
                organizations.findById(org).orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        Voucher voucher = new Voucher();
        voucher.setOrganizationId(org);
        applyHeader(voucher, request);
        voucher.setStatus(VoucherStatus.APPROVED);
        voucher.setVoucherNumber(
                sequences.nextNumber(org, branchId, type, organization.getFinancialYearStart(), request.voucherDate()));
        replaceLines(voucher, request.lines());
        recalculateTotals(voucher, totals);
        Voucher saved = vouchers.save(voucher);
        history.record(
                ENTITY_TYPE, saved.getId(), "CREATED_FROM_DOCUMENT", "Voucher created from " + referenceType, null);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public VoucherResponse get(UUID id) {
        return toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public Page<VoucherResponse> list(
            VoucherType type,
            VoucherStatus status,
            LocalDate from,
            LocalDate to,
            String search,
            UUID branchId,
            Pageable pageable) {
        UUID org = TenantContext.getOrganizationId();
        Specification<Voucher> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), org));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (type != null) {
                predicates.add(cb.equal(root.get("voucherType"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("voucherDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("voucherDate"), to));
            }
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("voucherNumber")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("narration"), "")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return vouchers.findAll(spec, pageable).map(this::toResponse);
    }

    Voucher loadEntity(UUID id) {
        return load(id);
    }

    private Voucher load(UUID id) {
        return vouchers.findByIdAndOrganizationIdAndDeletedAtIsNull(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found: " + id));
    }

    private void applyHeader(Voucher voucher, VoucherRequest request) {
        if (request.voucherType() == null) {
            throw new BusinessException("Voucher type is required");
        }
        if (request.voucherDate() == null) {
            throw new BusinessException("Voucher date is required");
        }
        voucher.setBranchId(request.branchId());
        voucher.setVoucherType(request.voucherType());
        voucher.setVoucherDate(request.voucherDate());
        voucher.setCurrencyCode(
                request.currencyCode() == null || request.currencyCode().isBlank() ? "INR" : request.currencyCode());
        voucher.setExchangeRate(request.exchangeRate() == null ? BigDecimal.ONE : request.exchangeRate());
        voucher.setReferenceType(request.referenceType());
        voucher.setReferenceId(request.referenceId());
        voucher.setNarration(request.narration());
        if (request.recurring() != null) {
            voucher.setRecurring(request.recurring());
        }
        voucher.setRecurringTemplateId(request.recurringTemplateId());
        voucher.setRecurrenceRule(request.recurrenceRule());
    }

    private void replaceLines(Voucher voucher, List<VoucherLineRequest> requests) {
        voucher.getLines().clear();
        int order = 0;
        for (VoucherLineRequest req : requests) {
            BigDecimal debit = AccountingMoney.normalize(req.debit());
            BigDecimal credit = AccountingMoney.normalize(req.credit());
            if (AccountingMoney.isZero(debit) && AccountingMoney.isZero(credit)) {
                continue;
            }
            VoucherLine line = new VoucherLine();
            line.setOrganizationId(voucher.getOrganizationId());
            line.setVoucher(voucher);
            line.setAccountId(req.accountId());
            line.setDebit(debit);
            line.setCredit(credit);
            line.setDescription(req.description());
            line.setCostCenterId(req.costCenterId());
            line.setDepartmentId(req.departmentId());
            line.setProjectId(req.projectId());
            line.setWarehouseId(req.warehouseId());
            line.setInventoryReference(req.inventoryReference());
            line.setTaxRateId(req.taxRateId());
            line.setTaxAmount(AccountingMoney.normalize(req.taxAmount()));
            line.setSortOrder(req.sortOrder() != null ? req.sortOrder() : order);
            voucher.getLines().add(line);
            order++;
        }
    }

    private void recalculateTotals(Voucher voucher, VoucherValidator.Totals totals) {
        voucher.setTotalDebit(totals.totalDebit());
        voucher.setTotalCredit(totals.totalCredit());
    }

    private static List<VoucherLineRequest> toLineRequests(List<VoucherLine> lines) {
        return lines.stream()
                .map(l -> new VoucherLineRequest(
                        l.getAccountId(),
                        l.getDebit(),
                        l.getCredit(),
                        l.getDescription(),
                        l.getCostCenterId(),
                        l.getDepartmentId(),
                        l.getProjectId(),
                        l.getWarehouseId(),
                        l.getInventoryReference(),
                        l.getTaxRateId(),
                        l.getTaxAmount(),
                        l.getSortOrder()))
                .toList();
    }

    private VoucherResponse toResponse(Voucher voucher) {
        List<VoucherLineResponse> lines = voucher.getLines().stream()
                .map(l -> new VoucherLineResponse(
                        l.getId(),
                        l.getAccountId(),
                        l.getDebit(),
                        l.getCredit(),
                        l.getDescription(),
                        l.getCostCenterId(),
                        l.getDepartmentId(),
                        l.getProjectId(),
                        l.getWarehouseId(),
                        l.getInventoryReference(),
                        l.getTaxRateId(),
                        l.getTaxAmount(),
                        l.getSortOrder()))
                .toList();
        return new VoucherResponse(
                voucher.getId(),
                voucher.getBranchId(),
                voucher.getVoucherNumber(),
                voucher.getVoucherType(),
                voucher.getVoucherDate(),
                voucher.getCurrencyCode(),
                voucher.getExchangeRate(),
                voucher.getReferenceType(),
                voucher.getReferenceId(),
                voucher.getNarration(),
                voucher.getStatus(),
                voucher.getTotalDebit(),
                voucher.getTotalCredit(),
                voucher.isPosted(),
                voucher.getPostedAt(),
                voucher.getPostedBy(),
                voucher.getJournalEntryId(),
                voucher.getReversedVoucherId(),
                voucher.getReversalOfId(),
                voucher.isRecurring(),
                voucher.getRecurringTemplateId(),
                voucher.getRecurrenceRule(),
                voucher.getVersion(),
                lines);
    }
}
