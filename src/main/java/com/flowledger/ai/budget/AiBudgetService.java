package com.flowledger.ai.budget;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.entity.AiUsageBudget;
import com.flowledger.ai.repository.AiUsageBudgetRepository;
import com.flowledger.common.tenant.TenantContext;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnAiEnabled
public class AiBudgetService {
    private final AiUsageBudgetRepository budgets;

    public AiBudgetService(AiUsageBudgetRepository budgets) {
        this.budgets = budgets;
    }

    @Transactional(readOnly = true)
    public AiDtos.BudgetStatusResponse status() {
        AiUsageBudget budget = getOrCreate(TenantContext.getOrganizationId());
        return toDto(budget);
    }

    @Transactional
    public void assertWithinBudget() {
        AiUsageBudget budget = getOrCreate(TenantContext.getOrganizationId());
        resetIfNewDay(budget);
        if (budget.isHardStop() && budget.getTokensUsedToday() >= budget.getDailyTokenLimit()) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "AI daily token budget exhausted for this organization");
        }
    }

    @Transactional
    public void recordUsage(int tokens) {
        if (tokens <= 0) {
            return;
        }
        AiUsageBudget budget = getOrCreate(TenantContext.getOrganizationId());
        resetIfNewDay(budget);
        budget.setTokensUsedToday(budget.getTokensUsedToday() + tokens);
        budgets.save(budget);
    }

    private AiUsageBudget getOrCreate(UUID orgId) {
        return budgets.findByOrganizationId(orgId).orElseGet(() -> {
            AiUsageBudget created = new AiUsageBudget();
            created.setOrganizationId(orgId);
            created.setUsageDate(LocalDate.now());
            return budgets.save(created);
        });
    }

    private void resetIfNewDay(AiUsageBudget budget) {
        LocalDate today = LocalDate.now();
        if (!today.equals(budget.getUsageDate())) {
            budget.setUsageDate(today);
            budget.setTokensUsedToday(0);
            budgets.save(budget);
        }
    }

    private AiDtos.BudgetStatusResponse toDto(AiUsageBudget budget) {
        resetIfNewDay(budget);
        boolean exhausted = budget.getTokensUsedToday() >= budget.getDailyTokenLimit();
        return new AiDtos.BudgetStatusResponse(
                budget.getOrganizationId(),
                budget.getDailyTokenLimit(),
                budget.getTokensUsedToday(),
                budget.isHardStop(),
                exhausted,
                budget.getUsageDate());
    }
}
