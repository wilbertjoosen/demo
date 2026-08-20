package com.example.user.service;

import com.example.user.model.Country;
import com.example.user.model.KeycloakUserSummary;
import com.example.user.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CountryServiceImpl implements CountryService {
    private final CountryRepository contryRepository;

    @Override
    public List<Country> list() {
        return contryRepository.findAll();
    }

    @Override
    public Country getByCode(String code) {
        return contryRepository.findByCode(code);
    }
}
