package com.example.comment;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends MongoRepository<Comment, String> {

    List<Comment> findByProductIdAndDeletedFalseOrderByCreatedAtAsc(String productId);

    Optional<Comment> findByIdAndDeletedFalse(String id);
}
