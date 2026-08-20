package com.example.user.config;

import com.example.user.model.Country;
import com.example.user.repository.CountryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DatabaseInitializer {

    @Bean
    CommandLineRunner initDatabase(CountryRepository countryRepository) {
        return args -> {
            if (countryRepository.count() == 0) {
                countryRepository.saveAll(List.of(
                        new Country("BR", "Brazil"),
                        new Country("NL", "Nederland"),
                        new Country("GE", "Germany")
                ));
            }
        };
    }
}