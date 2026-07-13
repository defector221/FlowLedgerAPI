package com.flowledger.report.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ReportFilter(
        LocalDate from, LocalDate to, UUID customer, UUID supplier, UUID product, UUID category, UUID warehouse) {}
