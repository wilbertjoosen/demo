package com.example.reporting;

import java.time.LocalDate;

public record DailyCount(LocalDate date, long count) {
}
