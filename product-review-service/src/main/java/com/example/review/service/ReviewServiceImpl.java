package com.example.review.service;
import com.example.review.model.Review;
import com.example.review.model.ReviewSummary;
import com.example.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;

    @Override
    public Review create(String productId, String keycloakUserId, String authorName, int rating, String body) {
        reviewRepository.findByProductIdAndKeycloakUserIdAndDeletedFalse(productId, keycloakUserId).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this product");
        });
        return reviewRepository.save(new Review(productId, keycloakUserId, authorName, rating, body));
    }

    @Override
    public List<Review> listByProduct(String productId) {
        return reviewRepository.findByProductIdAndDeletedFalseOrderByCreatedAtDesc(productId);
    }

    @Override
    public ReviewSummary summarize(String productId) {
        List<Review> reviews = listByProduct(productId);
        double average = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        return new ReviewSummary(productId, average, reviews.size());
    }

    @Override
    public void delete(String id, String requesterKeycloakId, boolean isAdmin) {
        Review review = reviewRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!isAdmin && !review.getKeycloakUserId().equals(requesterKeycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own reviews");
        }
        review.markDeleted();
        reviewRepository.save(review);
    }
}
