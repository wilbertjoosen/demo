package com.example.comment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;

    @Override
    public Comment create(String productId, String parentId, String keycloakUserId, String authorName, String body) {
        return commentRepository.save(new Comment(productId, parentId, keycloakUserId, authorName, body));
    }

    @Override
    public List<Comment> listByProduct(String productId) {
        return commentRepository.findByProductIdAndDeletedFalseOrderByCreatedAtAsc(productId);
    }

    @Override
    public void delete(String id, String requesterKeycloakId, boolean isAdmin) {
        Comment comment = commentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!isAdmin && !comment.getKeycloakUserId().equals(requesterKeycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own comments");
        }
        comment.markDeleted();
        commentRepository.save(comment);
    }
}
