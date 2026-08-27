package com.example.review;
import com.example.review.model.Review;
import com.example.review.model.ReviewSummary;
import com.example.review.repository.ReviewRepository;
import com.example.review.service.ReviewServiceImpl;

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
class ReviewServiceImplTest {

    @Mock
    ReviewRepository reviewRepository;

    ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewServiceImpl(reviewRepository);
    }

    @Test
    void create_firstReviewForProduct_succeeds() {
        when(reviewRepository.findByProductIdAndKeycloakUserIdAndDeletedFalse("p1", "user-1")).thenReturn(Optional.empty());
        Review review = new Review("p1", "user-1", "Alice", 5, "Great!");
        when(reviewRepository.save(any(Review.class))).thenReturn(review);

        Review result = reviewService.create("p1", "user-1", "Alice", 5, "Great!");

        assertThat(result).isEqualTo(review);
    }

    @Test
    void create_secondReviewFromSameUser_throwsConflict() {
        when(reviewRepository.findByProductIdAndKeycloakUserIdAndDeletedFalse("p1", "user-1"))
                .thenReturn(Optional.of(new Review("p1", "user-1", "Alice", 4, "Good")));

        assertThatThrownBy(() -> reviewService.create("p1", "user-1", "Alice", 5, "Great!"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void summarize_averagesRatingsAcrossReviews() {
        when(reviewRepository.findByProductIdAndDeletedFalseOrderByCreatedAtDesc("p1")).thenReturn(List.of(
                new Review("p1", "user-1", "Alice", 5, "Great"),
                new Review("p1", "user-2", "Bob", 3, "Okay")));

        ReviewSummary summary = reviewService.summarize("p1");

        assertThat(summary.productId()).isEqualTo("p1");
        assertThat(summary.averageRating()).isEqualTo(4.0);
        assertThat(summary.reviewCount()).isEqualTo(2);
    }

    @Test
    void summarize_noReviews_returnsZeroAverage() {
        when(reviewRepository.findByProductIdAndDeletedFalseOrderByCreatedAtDesc("p1")).thenReturn(List.of());

        ReviewSummary summary = reviewService.summarize("p1");

        assertThat(summary.averageRating()).isEqualTo(0.0);
        assertThat(summary.reviewCount()).isEqualTo(0);
    }

    @Test
    void delete_ownReview_marksDeleted() {
        Review review = new Review("p1", "user-1", "Alice", 5, "Great");
        when(reviewRepository.findByIdAndDeletedFalse("r1")).thenReturn(Optional.of(review));

        reviewService.delete("r1", "user-1", false);

        assertThat(review.isDeleted()).isTrue();
        verify(reviewRepository).save(review);
    }

    @Test
    void delete_otherUsersReview_notAdmin_throwsForbidden() {
        Review review = new Review("p1", "user-1", "Alice", 5, "Great");
        when(reviewRepository.findByIdAndDeletedFalse("r1")).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.delete("r1", "user-2", false))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void delete_notFound_throwsNotFound() {
        when(reviewRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.delete("missing", "user-1", false))
                .isInstanceOf(ResponseStatusException.class);
    }
}
