package com.example.comment;

import java.util.List;

public interface CommentService {

    Comment create(String productId, String parentId, String keycloakUserId, String authorName, String body);

    List<Comment> listByProduct(String productId);

    /** Author or admin only — enforced here since ownership can't be expressed via a static {@code @PreAuthorize}. */
    void delete(String id, String requesterKeycloakId, boolean isAdmin);
}
