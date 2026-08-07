package com.example.audit;

import java.time.Instant;
import java.util.List;

public record RecordHistoryEntry(Instant timestamp, String service, String principal, String type, String outcome,
                                  String action, List<FieldChange> changes) {
}
