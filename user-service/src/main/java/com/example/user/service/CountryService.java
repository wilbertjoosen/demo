package com.example.user.service;

import com.example.user.model.Country;

import java.util.List;

public interface CountryService {
    List<Country> list();
    Country getByCode(String code);
}
