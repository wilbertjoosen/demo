package com.example.chat;

import java.time.Instant;

/** One row in a user's inbox: who the conversation is with, a preview of the last message, and how many are unread. */
public record ConversationSummary(String id, String otherParticipantId, String otherParticipantUsername,
                                   Instant lastMessageAt, String lastMessagePreview, long unreadCount) {
}
