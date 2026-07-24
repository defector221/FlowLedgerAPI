package com.flowledger.finance.voucher.repository;

import com.flowledger.finance.voucher.domain.VoucherStatus;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.finance.voucher.entity.Voucher;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoucherRepository extends JpaRepository<Voucher, UUID>, JpaSpecificationExecutor<Voucher> {

    Optional<Voucher> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    Optional<Voucher> findByOrganizationIdAndReferenceTypeAndReferenceIdAndDeletedAtIsNull(
            UUID organizationId, String referenceType, UUID referenceId);

    boolean existsByOrganizationIdAndVoucherNumber(UUID organizationId, String voucherNumber);

    @Query(
            """
            select v from Voucher v
            where v.organizationId = :org
              and v.deletedAt is null
              and (:type is null or v.voucherType = :type)
              and (:status is null or v.status = :status)
              and (:from is null or v.voucherDate >= :from)
              and (:to is null or v.voucherDate <= :to)
              and (:branchId is null or v.branchId = :branchId)
              and (:search is null or :search = ''
                   or lower(v.voucherNumber) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(v.narration, '')) like lower(concat('%', cast(:search as string), '%')))
            order by v.voucherDate desc, v.createdAt desc
            """)
    Page<Voucher> search(
            @Param("org") UUID org,
            @Param("type") VoucherType type,
            @Param("status") VoucherStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("branchId") UUID branchId,
            @Param("search") String search,
            Pageable pageable);

    @Query(
            """
            select v from Voucher v
            where v.organizationId = :org
              and v.deletedAt is null
              and v.status = :status
              and v.voucherDate >= :from
              and v.voucherDate <= :to
            order by v.voucherDate asc, v.createdAt asc
            """)
    List<Voucher> findPostedByOrganizationAndDateBetween(
            @Param("org") UUID org,
            @Param("status") VoucherStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
