package com.example.reporting.model;

import java.time.LocalDate;

public record DailyCount(LocalDate date, long count) {
}
