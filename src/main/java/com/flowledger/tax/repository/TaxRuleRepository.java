package com.flowledger.tax.repository;

import com.flowledger.tax.entity.TaxRule;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxRuleRepository extends JpaRepository<TaxRule, UUID> {
    Optional<TaxRule> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<TaxRule> findByOrganizationIdAndTaxCategoryIdOrderByEffectiveFromDesc(UUID organizationId, UUID taxCategoryId);

    List<TaxRule> findByOrganizationIdAndTaxCategoryIdAndActiveTrueOrderByEffectiveFromDesc(
            UUID organizationId, UUID taxCategoryId);

    @Query(
            """
        select r from TaxRule r
        where r.organizationId = :organizationId
          and r.taxCategoryId = :categoryId
          and r.countryCode = :countryCode
          and r.active = true
          and r.supersededBy is null
          and r.effectiveFrom <= :date
          and (r.effectiveTo is null or r.effectiveTo >= :date)
          and (r.stateCode is null or r.stateCode = :stateCode)
        order by r.priority desc,
                 case when r.stateCode is not null then 1 else 0 end desc,
                 r.effectiveFrom desc
        """)
    List<TaxRule> findApplicableRules(
            @Param("organizationId") UUID organizationId,
            @Param("categoryId") UUID categoryId,
            @Param("countryCode") String countryCode,
            @Param("stateCode") String stateCode,
            @Param("date") LocalDate date);

    default Optional<TaxRule> findApplicableRule(
            UUID organizationId, UUID categoryId, String countryCode, String stateCode, LocalDate date) {
        List<TaxRule> rules = findApplicableRules(organizationId, categoryId, countryCode, stateCode, date);
        return rules.isEmpty() ? Optional.empty() : Optional.of(rules.get(0));
    }
}
