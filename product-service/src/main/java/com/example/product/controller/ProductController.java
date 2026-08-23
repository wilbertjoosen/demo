package com.example.product.controller;

import com.example.product.model.Product;
import com.example.product.model.ProductModelAssembler;
import com.example.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductModelAssembler assembler;
    private final JobLauncher jobLauncher;
    private final Job productImportJob;
    private final MessageSource messageSource;

    @GetMapping
    public CollectionModel<EntityModel<Product>> list() {
        return assembler.toCollectionModel(productService.list());
    }

    @GetMapping("/{id}")
    public EntityModel<Product> get(@PathVariable String id) {
        return assembler.toModel(productService.get(id));
    }

    public record CreateProductRequest(@NotBlank String sku, @NotBlank String name,
                                        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal price) {
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<EntityModel<Product>> create(@Valid @RequestBody CreateProductRequest request) {
        Product saved = productService.create(new Product(request.sku(), request.name(), request.price()));
        EntityModel<Product> model = assembler.toModel(saved);
        return ResponseEntity.created(URI.create(model.getRequiredLink("self").getHref())).body(model);
    }

    /** Every field optional — {@link ProductServiceImpl#update} applies only the ones present. */
    public record UpdateProductRequest(String sku, String name,
                                        @DecimalMin(value = "0.0", inclusive = false) BigDecimal price) {
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public EntityModel<Product> update(@PathVariable String id, @Valid @RequestBody UpdateProductRequest request) {
        Product patch = new Product(request.sku(), request.name(), request.price());
        return assembler.toModel(productService.update(id, patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Map<String, String>> importProducts() {
        try {
            var execution = jobLauncher.run(productImportJob,
                    new JobParametersBuilder().addLong("startedAt", System.currentTimeMillis()).toJobParameters());
            return ResponseEntity.accepted().body(Map.of(
                    "jobExecutionId", String.valueOf(execution.getId()),
                    "status", execution.getStatus().toString()
            ));
        } catch (Exception e) {
            String message = messageSource.getMessage(
                    "product.batchJobFailed", new Object[]{e.getMessage()}, LocaleContextHolder.getLocale());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message, e);
        }
    }
}
