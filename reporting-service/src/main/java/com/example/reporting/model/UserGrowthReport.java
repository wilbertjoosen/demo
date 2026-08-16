package com.example.reporting.model;

import java.util.List;

public record UserGrowthReport(long totalNewUsers, List<DailyCount> dailyRegistrations) {
}
