package com.example.reporting.model;

import java.util.Map;

public record SagaHealthReport(
        long inProgressCount,
        long completedCount,
        long cancelledCount,
        double cancellationRate,
        Double avgTimeToConfirmationMinutes,
        Map<String, Long> failuresByStage) {
}
