package com.example.chat.repository;

import com.example.chat.model.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    Optional<Conversation> findByParticipantIds(List<String> participantIds);

    List<Conversation> findByParticipantIdsContainingOrderByLastMessageAtDesc(String participantId);
}
