package com.example.chat;
import com.example.chat.model.ChatMessage;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.service.ChatServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    ChatMessageRepository chatMessageRepository;

    ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl(chatMessageRepository);
    }

    @Test
    void save_persistsAMessageForTheGivenProductRoom() {
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessage result = chatService.save("product-1", "alice", "hello everyone");

        assertThat(result.getProductId()).isEqualTo("product-1");
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getBody()).isEqualTo("hello everyone");
    }

    @Test
    void recentHistory_reversesRepositoryOrderToOldestFirst() {
        ChatMessage newer = new ChatMessage("product-1", "bob", "second");
        ChatMessage older = new ChatMessage("product-1", "alice", "first");
        when(chatMessageRepository.findByProductIdOrderByCreatedAtDesc("product-1", PageRequest.of(0, 50)))
                .thenReturn(new ArrayList<>(List.of(newer, older)));

        List<ChatMessage> result = chatService.recentHistory("product-1", 50);

        assertThat(result).containsExactly(older, newer);
        verify(chatMessageRepository).findByProductIdOrderByCreatedAtDesc("product-1", PageRequest.of(0, 50));
    }
}
