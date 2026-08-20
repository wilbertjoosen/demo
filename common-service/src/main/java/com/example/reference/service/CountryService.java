package com.example.reference.service;

import com.example.reference.model.Country;

import java.util.List;

public interface CountryService {
    List<Country> list();
    Country getByCode(String code);
}
