package com.example.delivery.controller;

import com.example.delivery.model.Delivery;
import com.example.delivery.model.DeliveryModelAssembler;
import com.example.delivery.service.DeliveryService;

import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final DeliveryModelAssembler assembler;

    @GetMapping
    public CollectionModel<EntityModel<Delivery>> list() {
        return assembler.toCollectionModel(deliveryService.list());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        deliveryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
