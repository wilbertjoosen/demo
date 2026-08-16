package com.example.shipping.controller;

import com.example.common.model.ShippingCarrier;
import com.example.shipping.model.Shipment;
import com.example.shipping.model.ShipmentModelAssembler;
import com.example.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

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

    /** Rate quote per carrier, used at checkout before an order/shipment exists. */
    @GetMapping("/quote")
    public Map<String, Object> quote(@RequestParam ShippingCarrier carrier, @RequestParam int quantity) {
        BigDecimal cost = shippingService.quote(carrier, quantity);
        return Map.of("carrier", carrier, "cost", cost);
    }

    @GetMapping("/order/{orderId}/tracking")
    public EntityModel<Shipment> tracking(@PathVariable String orderId) {
        return assembler.toModel(shippingService.getByOrderId(orderId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SHIPPING_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        shippingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Confirms a PENDING_WAREHOUSE shipment has been picked and handed to the carrier. */
    @PostMapping("/{id}/confirm-picked")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public ResponseEntity<Void> confirmPicked(@PathVariable String id) {
        shippingService.confirmPicked(id);
        return ResponseEntity.noContent().build();
    }

    public record ReportIssueRequest(String reason) {
    }

    /** Reports that a PENDING_WAREHOUSE shipment can't be fulfilled — triggers the same compensation path a system failure would. */
    @PostMapping("/{id}/report-issue")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public ResponseEntity<Void> reportIssue(@PathVariable String id, @RequestBody(required = false) ReportIssueRequest request) {
        shippingService.reportIssue(id, request == null ? null : request.reason());
        return ResponseEntity.noContent().build();
    }
}
