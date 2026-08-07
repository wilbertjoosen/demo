package com.example.user;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByKeycloakId(String keycloakId);

    List<User> findByDeletedFalse();

    Optional<User> findByIdAndDeletedFalse(String id);
}
