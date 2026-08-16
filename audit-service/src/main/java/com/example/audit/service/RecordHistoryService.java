package com.example.audit.service;
import com.example.audit.model.AuditLogDocument;
import com.example.audit.model.FieldChange;
import com.example.audit.model.RecordHistoryEntry;
import com.example.audit.repository.AuditLogRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reconstructs a record's change history from the audit trail. There's no explicit before/after
 * diff stored per event — instead, every successful write's {@code responseBody} is a full entity
 * snapshot (see RestCallAuditAspect), so the diff for one event is just "compare this snapshot to
 * the previous one for the same recordId". Read-only calls (no responseBody) still appear in the
 * timeline as VIEWED entries but don't move the snapshot baseline forward.
 */
@Service
@RequiredArgsConstructor
public class RecordHistoryService {

    /** Metadata about the record itself, not a business field change — excluded from diffs. */
    private static final Set<String> IGNORED_FIELDS = Set.of("links", "_links", "createdAt", "updatedAt", "lastModifiedBy");

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<RecordHistoryEntry> historyFor(String recordId) {
        List<AuditLogDocument> events = auditLogRepository.findByRecordId(recordId, Sort.by(Sort.Direction.ASC, "timestamp"));
        List<RecordHistoryEntry> history = new ArrayList<>();
        Map<String, Object> previousState = null;

        for (AuditLogDocument event : events) {
            Map<String, Object> data = event.getData();
            String outcome = String.valueOf(data.get("outcome"));
            Map<String, Object> newState = parseSnapshot((String) data.get("responseBody"));

            String action;
            List<FieldChange> changes;
            if (!"SUCCESS".equals(outcome)) {
                action = "FAILED";
                changes = List.of();
            } else if (newState == null) {
                action = "VIEWED";
                changes = List.of();
            } else if (previousState == null) {
                action = "CREATED";
                changes = List.of();
            } else {
                changes = diff(previousState, newState);
                action = changes.isEmpty() ? "NO_CHANGE" : "UPDATED";
            }
            if (newState != null) {
                previousState = newState;
            }

            history.add(new RecordHistoryEntry(event.getTimestamp(), event.getService(), event.getPrincipal(),
                    event.getType(), outcome, action, changes));
        }
        return history;
    }

    /** Returns null on missing/unparseable JSON (e.g. a response body truncated by RestCallAuditAspect's length cap). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSnapshot(String responseBodyJson) {
        if (responseBodyJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(responseBodyJson, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private List<FieldChange> diff(Map<String, Object> oldState, Map<String, Object> newState) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(oldState.keySet());
        keys.addAll(newState.keySet());
        List<FieldChange> changes = new ArrayList<>();
        for (String key : keys) {
            if (IGNORED_FIELDS.contains(key)) {
                continue;
            }
            Object oldValue = oldState.get(key);
            Object newValue = newState.get(key);
            if (!Objects.equals(oldValue, newValue)) {
                changes.add(new FieldChange(key, oldValue, newValue));
            }
        }
        return changes;
    }
}
