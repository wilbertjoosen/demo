package com.example.payment.controller;

import com.example.common.model.PaymentMethod;
import com.example.payment.dto.GatewayAvailabilityRequest;
import com.example.payment.dto.RejectPaymentRequest;
import com.example.payment.model.Payment;
import com.example.payment.model.PaymentModelAssembler;
import com.example.payment.service.PaymentProofStorageService;
import com.example.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentModelAssembler assembler;
    private final PaymentProofStorageService proofStorageService;

    @GetMapping
    public CollectionModel<EntityModel<Payment>> list() {
        return assembler.toCollectionModel(paymentService.list());
    }

    @GetMapping("/order/{orderId}")
    public EntityModel<Payment> byOrderId(@PathVariable String orderId) {
        return assembler.toModel(paymentService.getByOrderId(orderId));
    }

    /**
     * Customer uploads their own bank transfer/cash receipt. The upload response's "url" is a path
     * relative to this service's own routing prefix (already proxied by the gateway at
     * /api/payments/**), same convention as product-media-service's upload endpoint.
     */
    @PostMapping(value = "/{id}/proof", consumes = "multipart/form-data")
    public EntityModel<Payment> uploadProof(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestParam("file") MultipartFile file) {
        String filename = proofStorageService.store(file);
        Payment payment = paymentService.attachProof(jwt.getSubject(), id, "/api/payments/files/" + filename);
        return assembler.toModel(payment);
    }

    @GetMapping("/files/{filename}")
    public ResponseEntity<Resource> serveProof(@PathVariable String filename) {
        Path path = proofStorageService.resolve(filename);
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            return ResponseEntity.ok()
                    .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable")
                    .body(resource);
        } catch (MalformedURLException _) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** Which payment methods are currently usable — checkout UI disables the rest. */
    @GetMapping("/methods")
    public Map<PaymentMethod, Boolean> methods() {
        return paymentService.availableMethods();
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

    /** Approves a BANK_TRANSFER/CASH payment sitting in AWAITING_REVIEW — advances the order to PAID. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<Void> approve(@PathVariable String id) {
        paymentService.approve(id);
        return ResponseEntity.noContent().build();
    }

    /** Rejects a BANK_TRANSFER/CASH payment sitting in AWAITING_REVIEW — triggers the same compensation path a declined charge would. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<Void> reject(@PathVariable String id, @RequestBody(required = false) RejectPaymentRequest request) {
        paymentService.reject(id, request == null ? null : request.reason());
        return ResponseEntity.noContent().build();
    }
}
