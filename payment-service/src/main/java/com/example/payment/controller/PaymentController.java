package com.example.payment.controller;

import com.example.payment.model.Payment;
import com.example.payment.model.PaymentModelAssembler;
import com.example.payment.service.PaymentService;

import com.example.common.model.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentModelAssembler assembler;

    @GetMapping
    public CollectionModel<EntityModel<Payment>> list() {
        return assembler.toCollectionModel(paymentService.list());
    }

    /** Which payment methods are currently usable — checkout UI disables the rest. */
    @GetMapping("/methods")
    public Map<PaymentMethod, Boolean> methods() {
        return paymentService.availableMethods();
    }

    public record GatewayAvailabilityRequest(boolean available) {
    }

    /** Admin-only: force a method's mock gateway up/down to demonstrate the unavailable path. */
    @PatchMapping("/methods/{method}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Map<PaymentMethod, Boolean> setMethodAvailability(@PathVariable PaymentMethod method,
                                                               @RequestBody GatewayAvailabilityRequest request) {
        paymentService.setMethodAvailability(method, request.available());
        return paymentService.availableMethods();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        paymentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
