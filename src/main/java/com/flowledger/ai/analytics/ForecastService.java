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
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Advisory demand / cash / inventory forecasts using exponential smoothing + MAPE tracking.
 */
@Service
@ConditionalOnAiEnabled
public class ForecastService {
    private static final double ALPHA = 0.35;

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
                    case "SALES", "DEMAND" -> salesExponentialSmoothing();
                    case "CASHFLOW" -> cashflowForecast();
                    case "INVENTORY" -> inventoryForecast();
                    default -> new ForecastResult(List.of(), Map.of(), null);
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
        if (computed.mape() != null) {
            resultJson.put("mape", computed.mape());
        }

        AiForecastRun run = new AiForecastRun();
        run.setOrganizationId(org);
        run.setForecastType(normalized);
        run.setStatus("COMPLETED");
        run.setMethod("exponential_smoothing");
        run.setMape(computed.mape());
        run.setParamsJson(Map.of("method", "exponential_smoothing", "alpha", ALPHA));
        run.setResultJson(resultJson);
        run.setCompletedAt(OffsetDateTime.now());
        repository.save(run);

        return new AiDtos.ForecastResponse(
                true,
                "Advisory forecast with exponential smoothing (not audited).",
                normalized,
                run.getId(),
                computed.points(),
                computed.summary());
    }

    private ForecastResult salesExponentialSmoothing() {
        List<SalesInvoice> invoices =
                salesInvoiceService.list(null, null, Pageable.unpaged()).content();
        Map<YearMonth, BigDecimal> series = new LinkedHashMap<>();
        YearMonth now = YearMonth.from(LocalDate.now());
        for (int i = 5; i >= 0; i--) {
            series.put(now.minusMonths(i), BigDecimal.ZERO);
        }
        for (SalesInvoice inv : invoices) {
            if (inv.getInvoiceDate() == null || inv.getGrandTotal() == null) {
                continue;
            }
            YearMonth ym = YearMonth.from(inv.getInvoiceDate());
            if (series.containsKey(ym)) {
                series.put(ym, series.get(ym).add(inv.getGrandTotal()));
            }
        }

        List<BigDecimal> actuals = new ArrayList<>(series.values());
        List<BigDecimal> fitted = new ArrayList<>();
        BigDecimal level = actuals.isEmpty() ? BigDecimal.ZERO : actuals.get(0);
        for (BigDecimal actual : actuals) {
            level = level.multiply(BigDecimal.valueOf(1 - ALPHA))
                    .add(actual.multiply(BigDecimal.valueOf(ALPHA)))
                    .setScale(2, RoundingMode.HALF_UP);
            fitted.add(level);
        }
        BigDecimal next = level.setScale(2, RoundingMode.HALF_UP);
        BigDecimal mape = computeMape(actuals, fitted);

        List<AiDtos.ForecastPoint> points = new ArrayList<>();
        int idx = 0;
        for (Map.Entry<YearMonth, BigDecimal> e : series.entrySet()) {
            points.add(new AiDtos.ForecastPoint(e.getKey().toString(), e.getValue(), fitted.get(idx++)));
        }
        YearMonth nextPeriod = now.plusMonths(1);
        points.add(new AiDtos.ForecastPoint(nextPeriod.toString(), BigDecimal.ZERO, next));

        Map<String, Object> summary = new HashMap<>();
        summary.put("nextPeriod", nextPeriod.toString());
        summary.put("forecast", next);
        summary.put("mape", mape);
        summary.put("alpha", ALPHA);
        summary.put("sampleInvoiceCount", invoices.size());
        summary.put("method", "exponential_smoothing");
        return new ForecastResult(points, summary, mape);
    }

    private ForecastResult cashflowForecast() {
        List<SalesInvoice> invoices =
                salesInvoiceService.list(null, null, Pageable.unpaged()).content();
        BigDecimal outstanding = invoices.stream()
                .map(SalesInvoice::getOutstandingAmount)
                .filter(a -> a != null && a.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collectedProxy = invoices.stream()
                .filter(i -> i.getGrandTotal() != null && i.getOutstandingAmount() != null)
                .map(i -> i.getGrandTotal().subtract(i.getOutstandingAmount()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedCollections = outstanding.multiply(bd("0.72")).setScale(2, RoundingMode.HALF_UP);
        List<AiDtos.ForecastPoint> points = List.of(
                new AiDtos.ForecastPoint("AR_OUTSTANDING", outstanding, expectedCollections),
                new AiDtos.ForecastPoint("COLLECTED_PROXY", collectedProxy, collectedProxy.multiply(bd("1.03"))));
        Map<String, Object> summary = new HashMap<>();
        summary.put("outstanding", outstanding);
        summary.put("expectedCollections30d", expectedCollections);
        summary.put("collectedProxy", collectedProxy);
        summary.put("method", "ar_ageing_proxy");
        summary.put("mape", null);
        return new ForecastResult(points, summary, null);
    }

    private ForecastResult inventoryForecast() {
        List<StockPosition> positions = inventoryService.stockOverview();
        BigDecimal totalQty = positions.stream()
                .map(StockPosition::available)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal projected = totalQty.multiply(bd("0.92")).setScale(2, RoundingMode.HALF_UP);
        List<AiDtos.ForecastPoint> points =
                List.of(new AiDtos.ForecastPoint("STOCK_AVAILABLE", totalQty, projected));
        Map<String, Object> summary = new HashMap<>();
        summary.put("skuCount", positions.size());
        summary.put("totalAvailable", totalQty);
        summary.put("projectedAvailable", projected);
        summary.put("method", "stock_burn_proxy");
        return new ForecastResult(points, summary, null);
    }

    private static BigDecimal computeMape(List<BigDecimal> actuals, List<BigDecimal> forecasts) {
        if (actuals.isEmpty() || forecasts.isEmpty()) {
            return null;
        }
        double sum = 0;
        int n = 0;
        for (int i = 0; i < Math.min(actuals.size(), forecasts.size()); i++) {
            BigDecimal a = actuals.get(i);
            BigDecimal f = forecasts.get(i);
            if (a == null || a.signum() == 0) {
                continue;
            }
            sum += a.subtract(f).abs().divide(a, 6, RoundingMode.HALF_UP).doubleValue();
            n++;
        }
        if (n == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum / n * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private record ForecastResult(List<AiDtos.ForecastPoint> points, Map<String, Object> summary, BigDecimal mape) {}
}
