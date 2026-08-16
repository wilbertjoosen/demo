package com.example.chat;

import com.example.chat.model.Conversation;
import com.example.chat.model.ConversationSummary;
import com.example.chat.model.DirectMessage;
import com.example.chat.repository.ConversationRepository;
import com.example.chat.repository.DirectMessageRepository;
import com.example.chat.service.ConversationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock
    ConversationRepository conversationRepository;
    @Mock
    DirectMessageRepository directMessageRepository;

    ConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConversationServiceImpl(conversationRepository, directMessageRepository);
    }

    private static Conversation conversationWithId(String id, List<String> participantIds, Map<String, String> usernames) {
        Conversation conversation = new Conversation(participantIds, usernames);
        try {
            var field = Conversation.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(conversation, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return conversation;
    }

    @Test
    void startOrGet_self_throwsBadRequest() {
        assertThatThrownBy(() -> service.startOrGet("user-1", "alice", "user-1", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("yourself");
    }

    @Test
    void startOrGet_sortsParticipantIdsRegardlessOfCallOrder() {
        when(conversationRepository.findByParticipantIds(List.of("user-1", "user-2"))).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        // "user-2" calling first, "user-1" as the other side — the stored pair must still be sorted.
        service.startOrGet("user-2", "bob", "user-1", "alice");

        verify(conversationRepository).findByParticipantIds(List.of("user-1", "user-2"));
    }

    @Test
    void startOrGet_existingConversation_returnsItWithoutCreatingANewOne() {
        Conversation existing = conversationWithId("conv-1", List.of("user-1", "user-2"), Map.of("user-1", "alice", "user-2", "bob"));
        when(conversationRepository.findByParticipantIds(List.of("user-1", "user-2"))).thenReturn(Optional.of(existing));

        Conversation result = service.startOrGet("user-1", "alice", "user-2", "bob");

        assertThat(result).isSameAs(existing);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void startOrGet_newConversation_snapshotsBothUsernames() {
        when(conversationRepository.findByParticipantIds(any())).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation result = service.startOrGet("user-1", "alice", "user-2", "bob");

        assertThat(result.getParticipantUsernames()).containsEntry("user-1", "alice").containsEntry("user-2", "bob");
    }

    @Test
    void assertParticipant_unknownConversation_throwsNotFound() {
        when(conversationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assertParticipant("missing", "user-1"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ResponseStatusException.class))
                .extracting(ResponseStatusException::getStatusCode)
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assertParticipant_notAParticipant_throwsForbidden() {
        Conversation conversation = conversationWithId("conv-1", List.of("user-1", "user-2"), Map.of());
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.assertParticipant("conv-1", "user-3"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ResponseStatusException.class))
                .extracting(ResponseStatusException::getStatusCode)
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void history_notAParticipant_throwsForbidden_andNeverQueriesMessages() {
        Conversation conversation = conversationWithId("conv-1", List.of("user-1", "user-2"), Map.of());
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.history("conv-1", "user-3", 50))
                .isInstanceOf(ResponseStatusException.class);

        verify(directMessageRepository, never()).findByConversationIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void history_returnsOldestFirst() {
        Conversation conversation = conversationWithId("conv-1", List.of("user-1", "user-2"), Map.of());
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conversation));
        DirectMessage newer = new DirectMessage("conv-1", "user-2", "bob", "second");
        DirectMessage older = new DirectMessage("conv-1", "user-1", "alice", "first");
        // Repository contract: newest-first — the service must reverse it for display.
        when(directMessageRepository.findByConversationIdOrderByCreatedAtDesc("conv-1", PageRequest.of(0, 50)))
                .thenReturn(new ArrayList<>(List.of(newer, older)));

        List<DirectMessage> result = service.history("conv-1", "user-1", 50);

        assertThat(result).containsExactly(older, newer);
    }

    @Test
    void sendMessage_persistsAndTouchesConversation() {
        Conversation conversation = conversationWithId("conv-1", List.of("user-1", "user-2"), Map.of());
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conversation));
        when(directMessageRepository.save(any(DirectMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        DirectMessage result = service.sendMessage("conv-1", "user-1", "alice", "hello");

        assertThat(result.getBody()).isEqualTo("hello");
        verify(conversationRepository, times(1)).save(conversation);
    }

    @Test
    void markDelivered_onlyMarksMessagesFromTheOtherParticipant() {
        Conversation conversation = conversationWithId("conv-1", List.of("user-1", "user-2"), Map.of());
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conversation));
        DirectMessage fromOther = new DirectMessage("conv-1", "user-2", "bob", "hi");
        when(directMessageRepository.findByConversationIdAndSenderIdNotAndDeliveredAtIsNull("conv-1", "user-1"))
                .thenReturn(new ArrayList<>(List.of(fromOther)));
        when(directMessageRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<DirectMessage> updated = service.markDelivered("conv-1", "user-1");

        assertThat(updated).hasSize(1);
        assertThat(fromOther.getDeliveredAt()).isNotNull();
    }

    @Test
    void markRead_alsoImpliesDelivered() {
        Conversation conversation = conversationWithId("conv-1", List.of("user-1", "user-2"), Map.of());
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conversation));
        DirectMessage fromOther = new DirectMessage("conv-1", "user-2", "bob", "hi");
        when(directMessageRepository.findByConversationIdAndSenderIdNotAndReadAtIsNull("conv-1", "user-1"))
                .thenReturn(new ArrayList<>(List.of(fromOther)));
        when(directMessageRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markRead("conv-1", "user-1");

        assertThat(fromOther.getDeliveredAt()).isNotNull();
        assertThat(fromOther.getReadAt()).isNotNull();
    }

    @Test
    void myConversations_computesOtherParticipantAndUnreadCount() {
        Conversation conversation = conversationWithId("conv-1", List.of("user-1", "user-2"), Map.of("user-1", "alice", "user-2", "bob"));
        when(conversationRepository.findByParticipantIdsContainingOrderByLastMessageAtDesc("user-1"))
                .thenReturn(List.of(conversation));
        when(directMessageRepository.findFirstByConversationIdOrderByCreatedAtDesc("conv-1")).thenReturn(Optional.empty());
        when(directMessageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull("conv-1", "user-1")).thenReturn(3L);

        List<ConversationSummary> result = service.myConversations("user-1");

        assertThat(result).hasSize(1);
        ConversationSummary summary = result.get(0);
        assertThat(summary.otherParticipantId()).isEqualTo("user-2");
        assertThat(summary.otherParticipantUsername()).isEqualTo("bob");
        assertThat(summary.unreadCount()).isEqualTo(3L);
    }
}
