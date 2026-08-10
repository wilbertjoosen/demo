package com.example.comment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    CommentRepository commentRepository;

    CommentServiceImpl commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentServiceImpl(commentRepository);
    }

    @Test
    void create_savesComment() {
        Comment comment = new Comment("p1", null, "user-1", "Alice", "Great product");
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);

        Comment result = commentService.create("p1", null, "user-1", "Alice", "Great product");

        assertThat(result).isEqualTo(comment);
    }

    @Test
    void listByProduct_returnsOrderedComments() {
        List<Comment> comments = List.of(new Comment("p1", null, "user-1", "Alice", "First"));
        when(commentRepository.findByProductIdAndDeletedFalseOrderByCreatedAtAsc("p1")).thenReturn(comments);

        assertThat(commentService.listByProduct("p1")).isEqualTo(comments);
    }

    @Test
    void update_ownComment_succeeds() {
        Comment comment = new Comment("p1", null, "user-1", "Alice", "Original");
        when(commentRepository.findByIdAndDeletedFalse("c1")).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment)).thenReturn(comment);

        Comment result = commentService.update("c1", "user-1", false, "Updated");

        assertThat(result.getBody()).isEqualTo("Updated");
    }

    @Test
    void update_otherUsersComment_notAdmin_throwsForbidden() {
        Comment comment = new Comment("p1", null, "user-1", "Alice", "Original");
        when(commentRepository.findByIdAndDeletedFalse("c1")).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update("c1", "user-2", false, "Hacked"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void update_otherUsersComment_asAdmin_succeeds() {
        Comment comment = new Comment("p1", null, "user-1", "Alice", "Original");
        when(commentRepository.findByIdAndDeletedFalse("c1")).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment)).thenReturn(comment);

        Comment result = commentService.update("c1", "admin-1", true, "Moderated");

        assertThat(result.getBody()).isEqualTo("Moderated");
    }

    @Test
    void update_notFound_throwsNotFound() {
        when(commentRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.update("missing", "user-1", false, "x"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void delete_ownComment_marksDeleted() {
        Comment comment = new Comment("p1", null, "user-1", "Alice", "Original");
        when(commentRepository.findByIdAndDeletedFalse("c1")).thenReturn(Optional.of(comment));

        commentService.delete("c1", "user-1", false);

        assertThat(comment.isDeleted()).isTrue();
        verify(commentRepository).save(comment);
    }

    @Test
    void delete_otherUsersComment_notAdmin_throwsForbidden() {
        Comment comment = new Comment("p1", null, "user-1", "Alice", "Original");
        when(commentRepository.findByIdAndDeletedFalse("c1")).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete("c1", "user-2", false))
                .isInstanceOf(ResponseStatusException.class);
    }
}
