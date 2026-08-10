package com.example.reporting;

import java.util.List;

public record UserGrowthReport(long totalNewUsers, List<DailyCount> dailyRegistrations) {
}
