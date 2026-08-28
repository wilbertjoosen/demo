package com.example.comment.repository;
import com.example.comment.model.Comment;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReadPreference;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends MongoRepository<Comment, String> {

    /** Public comment listing — see product-service's ProductRepository for the full reasoning on this pattern. */
    @ReadPreference("secondaryPreferred")
    List<Comment> findByProductIdAndDeletedFalseOrderByCreatedAtAsc(String productId);

    Optional<Comment> findByIdAndDeletedFalse(String id);
}
