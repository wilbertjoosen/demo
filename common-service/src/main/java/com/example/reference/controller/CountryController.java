package com.example.reference.controller;

import com.example.reference.model.Country;
import com.example.reference.model.CountryModelAssembler;
import com.example.reference.service.CountryService;

import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/countries")
@RequiredArgsConstructor
public class CountryController {

    private final CountryService countryService;
    private final CountryModelAssembler countryAssembler;

    @GetMapping
    public CollectionModel<EntityModel<Country>> list() {
        return countryAssembler.toCollectionModel(countryService.list());
    }

    @GetMapping("/{code}")
    public EntityModel<Country> getByCode(@PathVariable String code) {
        return countryAssembler.toModel(countryService.getByCode(code));
    }
}
