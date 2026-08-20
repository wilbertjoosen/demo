package com.example.reference.repository;

import com.example.reference.model.Country;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CountryRepository extends MongoRepository<Country, String> {
    Optional<Country> findByCode(String code);
}
