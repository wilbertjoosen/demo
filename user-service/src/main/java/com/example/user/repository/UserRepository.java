package com.example.user.repository;
import com.example.user.model.User;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByKeycloakId(String keycloakId);

    List<User> findByDeletedFalse();

    Optional<User> findByIdAndDeletedFalse(String id);

    Optional<User> findByNationalIdAndDeletedFalse(String nationalId);
}
