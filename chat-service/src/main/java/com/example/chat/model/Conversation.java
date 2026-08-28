package com.example.chat.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A private 1:1 conversation between two Keycloak users, independent of the per-product public
 * chat rooms. Compound index serves both findByParticipantIds (exact match) and
 * findByParticipantIdsContainingOrderByLastMessageAtDesc — participantIds is the only array field
 * here, so it's safe to compound with lastMessageAt (Mongo disallows compounding two array fields,
 * not one array plus scalars).
 */
@CompoundIndex(def = "{'participantIds': 1, 'lastMessageAt': -1}")
@Document(collection = "conversations")
@Getter
@NoArgsConstructor
public class Conversation {

    @Id
    private String id;

    /** Always exactly 2 keycloak IDs, sorted, so a pair maps to one conversation regardless of who started it. */
    private List<String> participantIds;

    /**
     * Display names snapshotted at creation time (self-reported via JWT / the directory lookup),
     * not re-synced if the user renames later.
     */
    private Map<String, String> participantUsernames;

    @CreatedDate
    private Instant createdAt;

    private Instant lastMessageAt;

    public Conversation(List<String> participantIds, Map<String, String> participantUsernames) {
        this.participantIds = participantIds;
        this.participantUsernames = participantUsernames;
        this.lastMessageAt = Instant.now();
    }

    public void touch() {
        this.lastMessageAt = Instant.now();
    }
}
