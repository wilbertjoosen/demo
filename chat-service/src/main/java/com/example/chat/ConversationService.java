package com.example.chat;

import java.util.List;

public interface ConversationService {

    List<ConversationSummary> myConversations(String myKeycloakId);

    /** Finds the existing conversation for this pair, or creates one. Usernames are snapshotted at creation time. */
    Conversation startOrGet(String myKeycloakId, String myUsername, String otherKeycloakId, String otherUsername);

    /** Most recent messages first from storage, returned oldest-first for display. Throws if the requester isn't a participant. */
    List<DirectMessage> history(String conversationId, String requesterKeycloakId, int limit);

    DirectMessage sendMessage(String conversationId, String senderId, String senderUsername, String body);

    /**
     * Marks every not-yet-delivered message sent by the OTHER participant as delivered to
     * {@code recipientId}. Returns the messages that changed.
     */
    List<DirectMessage> markDelivered(String conversationId, String recipientId);

    /** Marks every not-yet-read message sent by the OTHER participant as read by {@code recipientId}. Returns the messages that changed. */
    List<DirectMessage> markRead(String conversationId, String recipientId);

    /** Throws 404 if the conversation doesn't exist, 403 if the caller isn't one of its two participants. */
    void assertParticipant(String conversationId, String keycloakId);
}
