package com.example.delivery;

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

    /** Confirms a PENDING_DELIVERY_AGENT delivery was handed to the customer. */
    @PostMapping("/{id}/confirm-delivered")
    @PreAuthorize("hasAnyRole('ADMIN', 'DELIVERY_AGENT')")
    public ResponseEntity<Void> confirmDelivered(@PathVariable String id) {
        deliveryService.confirmDelivered(id);
        return ResponseEntity.noContent().build();
    }

    public record ReportIssueRequest(String reason) {
    }

    /** Reports that a PENDING_DELIVERY_AGENT delivery couldn't be completed — triggers the same compensation a system failure would. */
    @PostMapping("/{id}/report-issue")
    @PreAuthorize("hasAnyRole('ADMIN', 'DELIVERY_AGENT')")
    public ResponseEntity<Void> reportIssue(@PathVariable String id, @RequestBody(required = false) ReportIssueRequest request) {
        deliveryService.reportIssue(id, request == null ? null : request.reason());
        return ResponseEntity.noContent().build();
    }
}
