package com.flowledger.migration.domain;

public enum ImportJobStatus {
    PENDING,
    UPLOADED,
    DETECTED,
    MAPPED,
    VALIDATED,
    QUEUED,
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED
}
