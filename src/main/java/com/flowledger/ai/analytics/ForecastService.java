package com.flowledger.ai.analytics;

import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.entity.AiForecastRun;
import com.flowledger.ai.repository.AiForecastRunRepository;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.inventory.dto.InventoryDtos.StockPosition;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.sales.service.SalesInvoiceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Advisory heuristic forecasts (moving-average stubs). Not for financial reporting or compliance.
 */
@Service
@ConditionalOnAiEnabled
public class ForecastService {
    private final AiProperties properties;
    private final AiForecastRunRepository repository;
    private final SalesInvoiceService salesInvoiceService;
    private final InventoryService inventoryService;

    public ForecastService(
            AiProperties properties,
            AiForecastRunRepository repository,
            SalesInvoiceService salesInvoiceService,
            InventoryService inventoryService) {
        this.properties = properties;
        this.repository = repository;
        this.salesInvoiceService = salesInvoiceService;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public AiDtos.ForecastResponse forecast(String type) {
        if (!properties.isAnalyticsEnabled()) {
            return new AiDtos.ForecastResponse(
                    false,
                    "Analytics disabled. Set flowledger.ai.analytics-enabled=true to enable advisory forecasts.",
                    type,
                    null,
                    List.of(),
                    Map.of());
        }
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DEMAND", "SALES", "CASHFLOW", "INVENTORY").contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "type must be DEMAND, SALES, CASHFLOW, or INVENTORY");
        }

        UUID org = TenantContext.getOrganizationId();
        ForecastResult computed =
                switch (normalized) {
                    case "SALES", "DEMAND" -> salesMovingAverage();
                    case "CASHFLOW" -> cashflowStub();
                    case "INVENTORY" -> inventoryStub();
                    default -> new ForecastResult(List.of(), Map.of());
                };

        Map<String, Object> resultJson = new HashMap<>(computed.summary());
        resultJson.put(
                "points",
                computed.points().stream()
                        .map(p -> Map.<String, Object>of(
                                "period", p.period(),
                                "actual", p.actual(),
                                "forecast", p.forecast()))
                        .toList());

        AiForecastRun run = new AiForecastRun();
        run.setOrganizationId(org);
        run.setForecastType(normalized);
        run.setStatus("COMPLETED");
        run.setParamsJson(Map.of("method", "moving_average_stub", "windowMonths", 3));
        run.setResultJson(resultJson);
        run.setCompletedAt(OffsetDateTime.now());
        repository.save(run);

        return new AiDtos.ForecastResponse(
                true,
                "Advisory heuristic forecast (not audited).",
                normalized,
                run.getId(),
                computed.points(),
                computed.summary());
    }

    private ForecastResult salesMovingAverage() {
        List<SalesInvoice> invoices = salesInvoiceService.list(null, null);
        Map<YearMonth, Integer> counts = new LinkedHashMap<>();
        YearMonth now = YearMonth.from(LocalDate.now());
        for (int i = 5; i >= 0; i--) {
            counts.put(now.minusMonths(i), 0);
        }
        for (SalesInvoice inv : invoices) {
            if (inv.getInvoiceDate() == null) {
                continue;
            }
            YearMonth ym = YearMonth.from(inv.getInvoiceDate());
            if (counts.containsKey(ym)) {
                counts.put(ym, counts.get(ym) + 1);
            }
        }

        List<AiDtos.ForecastPoint> points = new ArrayList<>();
        List<Integer> recentCounts = new ArrayList<>();
        for (Map.Entry<YearMonth, Integer> e : counts.entrySet()) {
            recentCounts.add(e.getValue());
            points.add(
                    new AiDtos.ForecastPoint(e.getKey().toString(), BigDecimal.valueOf(e.getValue()), BigDecimal.ZERO));
        }
        double avg = recentCounts.stream().mapToInt(Integer::intValue).average().orElse(0);
        BigDecimal forecastNext = BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
        YearMonth next = now.plusMonths(1);
        points.add(new AiDtos.ForecastPoint(next.toString(), BigDecimal.ZERO, forecastNext));

        Map<String, Object> summary = new HashMap<>();
        summary.put("nextPeriod", next.toString());
        summary.put("movingAverage", forecastNext);
        summary.put("sampleInvoiceCount", invoices.size());
        summary.put("method", "invoice-count moving average stub");
        return new ForecastResult(points, summary);
    }

    private ForecastResult cashflowStub() {
        List<SalesInvoice> invoices = salesInvoiceService.list(null, null);
        BigDecimal outstanding = invoices.stream()
                .map(SalesInvoice::getOutstandingAmount)
                .filter(a -> a != null && a.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collectedProxy = invoices.stream()
                .filter(i -> i.getGrandTotal() != null && i.getOutstandingAmount() != null)
                .map(i -> i.getGrandTotal().subtract(i.getOutstandingAmount()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AiDtos.ForecastPoint> points = List.of(
                new AiDtos.ForecastPoint("AR_OUTSTANDING", outstanding, outstanding.multiply(bd("0.9"))),
                new AiDtos.ForecastPoint("COLLECTED_PROXY", collectedProxy, collectedProxy.multiply(bd("1.05"))));
        Map<String, Object> summary = new HashMap<>();
        summary.put("outstanding", outstanding);
        summary.put("collectedProxy", collectedProxy);
        summary.put("method", "AR outstanding / collected proxy stub");
        return new ForecastResult(points, summary);
    }

    private ForecastResult inventoryStub() {
        List<StockPosition> positions = inventoryService.stockOverview();
        BigDecimal totalQty = positions.stream()
                .map(StockPosition::available)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = positions.isEmpty()
                ? BigDecimal.ZERO
                : totalQty.divide(BigDecimal.valueOf(positions.size()), 2, RoundingMode.HALF_UP);
        List<AiDtos.ForecastPoint> points =
                List.of(new AiDtos.ForecastPoint("STOCK_AVAILABLE", totalQty, totalQty.multiply(bd("0.95"))));
        Map<String, Object> summary = new HashMap<>();
        summary.put("skuCount", positions.size());
        summary.put("totalAvailable", totalQty);
        summary.put("avgPerSku", avg);
        summary.put("method", "inventory position stub");
        return new ForecastResult(points, summary);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private record ForecastResult(List<AiDtos.ForecastPoint> points, Map<String, Object> summary) {}
}
