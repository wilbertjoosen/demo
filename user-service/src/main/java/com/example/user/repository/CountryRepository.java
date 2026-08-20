package com.example.user.repository;

import com.example.user.model.Country;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CountryRepository extends MongoRepository<Country, String> {
    Country findByCode(String code);
}
