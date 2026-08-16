package com.example.inventory.controller;
import com.example.inventory.model.InventoryItem;
import com.example.inventory.model.InventoryModelAssembler;
import com.example.inventory.service.InventoryService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryModelAssembler assembler;

    public record AggregateStock(String productId, int totalQuantity) {
    }

    @GetMapping("/{productId}")
    public EntityModel<AggregateStock> aggregate(@PathVariable String productId) {
        AggregateStock stock = new AggregateStock(productId, inventoryService.aggregateStock(productId));
        return EntityModel.of(stock,
                linkTo(methodOn(InventoryController.class).aggregate(productId)).withSelfRel(),
                linkTo(methodOn(InventoryController.class).warehouses(productId)).withRel("warehouses"));
    }

    @GetMapping("/{productId}/warehouses")
    public CollectionModel<EntityModel<InventoryItem>> warehouses(@PathVariable String productId) {
        return assembler.toCollectionModel(inventoryService.listByProduct(productId), productId);
    }

    public record ReserveRequest(@Min(1) int quantity) {
    }

    @PostMapping("/{productId}/reserve")
    public ResponseEntity<Void> reserve(@PathVariable String productId, @Valid @RequestBody ReserveRequest request) {
        boolean reserved = inventoryService.reserve(productId, request.quantity());
        return reserved ? ResponseEntity.noContent().build() : ResponseEntity.status(409).build();
    }

    @PostMapping("/{productId}/release")
    public ResponseEntity<Void> release(@PathVariable String productId, @Valid @RequestBody ReserveRequest request) {
        inventoryService.release(productId, request.quantity());
        return ResponseEntity.noContent().build();
    }

    public record AddStockRequest(@NotBlank String warehouseId, @Min(1) int quantity) {
    }

    @PostMapping("/{productId}/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    public EntityModel<InventoryItem> addStock(@PathVariable String productId, @Valid @RequestBody AddStockRequest request) {
        return assembler.toModel(inventoryService.addStock(productId, request.warehouseId(), request.quantity()));
    }

    /** {@code itemId} is the specific per-warehouse line's own id, not the productId. */
    @DeleteMapping("/{productId}/warehouses/{itemId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String productId, @PathVariable String itemId) {
        inventoryService.delete(itemId);
        return ResponseEntity.noContent().build();
    }
}
