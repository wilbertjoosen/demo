package com.example.shipping;

import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShippingService shippingService;
    private final ShipmentModelAssembler assembler;

    @GetMapping
    public CollectionModel<EntityModel<Shipment>> list() {
        return assembler.toCollectionModel(shippingService.list());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        shippingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
