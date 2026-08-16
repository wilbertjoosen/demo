package com.example.review.service;

import com.example.review.model.Review;
import com.example.review.model.ReviewSummary;

import java.util.List;

public interface ReviewService {

    Review create(String productId, String keycloakUserId, String authorName, int rating, String body);

    List<Review> listByProduct(String productId);

    ReviewSummary summarize(String productId);

    /** Author or admin only — enforced here since ownership can't be expressed via a static {@code @PreAuthorize}. */
    void delete(String id, String requesterKeycloakId, boolean isAdmin);
}
