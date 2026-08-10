package com.example.reporting;

import java.time.Instant;

public record UserDrillDownItem(String userId, String username, String email, Instant registeredAt) {
}
