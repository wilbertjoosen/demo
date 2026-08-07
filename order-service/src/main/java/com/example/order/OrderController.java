package com.example.order;

import com.example.common.model.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderViewModelAssembler assembler;

    public record PlaceOrderRequest(
            @NotBlank String productId,
            @Min(1) int quantity,
            @NotNull @Valid Address shippingAddress) {
    }

    @PostMapping
    public ResponseEntity<EntityModel<OrderView>> placeOrder(@AuthenticationPrincipal Jwt jwt,
                                                               @Valid @RequestBody PlaceOrderRequest request) {
        Order order = orderService.placeOrder(jwt.getSubject(), jwt.getClaimAsString("email"),
                request.productId(), request.quantity(), request.shippingAddress());
        EntityModel<OrderView> model = assembler.toModel(orderService.getOrderView(order.getId().toString()));
        return ResponseEntity.created(URI.create(model.getRequiredLink("self").getHref())).body(model);
    }

    @GetMapping
    public CollectionModel<EntityModel<OrderView>> myOrders(@AuthenticationPrincipal Jwt jwt) {
        return assembler.toCollectionModel(orderService.listOrders(jwt.getSubject()));
    }

    @GetMapping("/{id}")
    public EntityModel<OrderView> getOrder(@PathVariable String id) {
        return assembler.toModel(orderService.getOrderView(id));
    }

    @PostMapping("/{id}/cancel")
    public EntityModel<OrderView> cancelOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return assembler.toModel(orderService.cancelOrder(jwt.getSubject(), id));
    }

    public record UpdateAddressRequest(@NotNull @Valid Address shippingAddress) {
    }

    /** Address-only — see OrderService#updateShippingAddress for why quantity isn't patchable here. */
    @PatchMapping("/{id}")
    public EntityModel<OrderView> updateOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                               @Valid @RequestBody UpdateAddressRequest request) {
        return assembler.toModel(orderService.updateShippingAddress(jwt.getSubject(), id, request.shippingAddress()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
