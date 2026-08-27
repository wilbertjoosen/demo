package com.example.audit;
import com.example.audit.model.AuditLogDocument;
import com.example.audit.model.FieldChange;
import com.example.audit.model.RecordHistoryEntry;
import com.example.audit.repository.AuditLogRepository;
import com.example.audit.service.RecordHistoryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordHistoryServiceTest {

    @Mock
    AuditLogRepository auditLogRepository;

    RecordHistoryService recordHistoryService;

    @BeforeEach
    void setUp() {
        recordHistoryService = new RecordHistoryService(auditLogRepository);
    }

    private AuditLogDocument event(String outcome, String responseBodyJson, Instant timestamp) {
        return new AuditLogDocument("product-service", "admin", "PUT",
                Map.of("outcome", outcome, "responseBody", responseBodyJson == null ? "" : responseBodyJson), timestamp);
    }

    @Test
    void firstEventWithSnapshot_isClassifiedCreated() {
        when(auditLogRepository.findByRecordId(eq("r1"), any(Sort.class))).thenReturn(List.of(
                event("SUCCESS", "{\"name\":\"Widget\",\"price\":9.99}", Instant.parse("2026-01-01T00:00:00Z"))));

        List<RecordHistoryEntry> history = recordHistoryService.historyFor("r1");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).action()).isEqualTo("CREATED");
        assertThat(history.get(0).changes()).isEmpty();
    }

    @Test
    void secondEventWithChangedField_isClassifiedUpdatedWithDiff() {
        when(auditLogRepository.findByRecordId(eq("r1"), any(Sort.class))).thenReturn(List.of(
                event("SUCCESS", "{\"name\":\"Widget\",\"price\":9.99}", Instant.parse("2026-01-01T00:00:00Z")),
                event("SUCCESS", "{\"name\":\"Widget\",\"price\":12.50}", Instant.parse("2026-01-02T00:00:00Z"))));

        List<RecordHistoryEntry> history = recordHistoryService.historyFor("r1");

        assertThat(history).hasSize(2);
        RecordHistoryEntry second = history.get(1);
        assertThat(second.action()).isEqualTo("UPDATED");
        assertThat(second.changes()).containsExactly(new FieldChange("price", 9.99, 12.5));
    }

    @Test
    void secondEventWithIdenticalSnapshot_isClassifiedNoChange() {
        when(auditLogRepository.findByRecordId(eq("r1"), any(Sort.class))).thenReturn(List.of(
                event("SUCCESS", "{\"name\":\"Widget\"}", Instant.parse("2026-01-01T00:00:00Z")),
                event("SUCCESS", "{\"name\":\"Widget\"}", Instant.parse("2026-01-02T00:00:00Z"))));

        List<RecordHistoryEntry> history = recordHistoryService.historyFor("r1");

        assertThat(history.get(1).action()).isEqualTo("NO_CHANGE");
        assertThat(history.get(1).changes()).isEmpty();
    }

    @Test
    void eventWithNoResponseBody_isClassifiedViewed() {
        when(auditLogRepository.findByRecordId(eq("r1"), any(Sort.class))).thenReturn(List.of(
                event("SUCCESS", null, Instant.parse("2026-01-01T00:00:00Z"))));

        List<RecordHistoryEntry> history = recordHistoryService.historyFor("r1");

        assertThat(history.get(0).action()).isEqualTo("VIEWED");
    }

    @Test
    void nonSuccessOutcome_isClassifiedFailed() {
        when(auditLogRepository.findByRecordId(eq("r1"), any(Sort.class))).thenReturn(List.of(
                event("ERROR", "{\"name\":\"Widget\"}", Instant.parse("2026-01-01T00:00:00Z"))));

        List<RecordHistoryEntry> history = recordHistoryService.historyFor("r1");

        assertThat(history.get(0).action()).isEqualTo("FAILED");
        assertThat(history.get(0).changes()).isEmpty();
    }

    @Test
    void diff_ignoresMetadataFields() {
        when(auditLogRepository.findByRecordId(eq("r1"), any(Sort.class))).thenReturn(List.of(
                event("SUCCESS", "{\"name\":\"Widget\",\"createdAt\":\"2026-01-01\"}", Instant.parse("2026-01-01T00:00:00Z")),
                event("SUCCESS", "{\"name\":\"Widget\",\"createdAt\":\"2026-01-02\"}", Instant.parse("2026-01-02T00:00:00Z"))));

        List<RecordHistoryEntry> history = recordHistoryService.historyFor("r1");

        assertThat(history.get(1).action()).isEqualTo("NO_CHANGE");
    }
}
