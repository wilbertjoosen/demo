package com.example.product;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventContracts;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    ProductRepository productRepository;
    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    MongoTemplate mongoTemplate;
    @Mock
    AuditorAware<String> auditorAware;

    ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productRepository, kafkaTemplate, mongoTemplate, auditorAware);
    }

    private Product savedProduct(String id, String sku, String name, BigDecimal price) {
        Product product = new Product(sku, name, price);
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return product;
    }

    @Test
    void list_returnsOnlyNonDeletedProducts() {
        List<Product> products = List.of(savedProduct("p1", "SKU-1", "Widget", BigDecimal.TEN));
        when(productRepository.findByDeletedFalse()).thenReturn(products);

        assertThat(productService.list()).isEqualTo(products);
    }

    @Test
    void get_found_returnsProduct() {
        Product product = savedProduct("p1", "SKU-1", "Widget", BigDecimal.TEN);
        when(productRepository.findByIdAndDeletedFalse("p1")).thenReturn(Optional.of(product));

        assertThat(productService.get("p1")).isEqualTo(product);
    }

    @Test
    void get_notFound_throwsNotFound() {
        when(productRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.get("missing")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void create_savesAndPublishesProductCreated() {
        Product toSave = new Product("SKU-1", "Widget", new BigDecimal("9.99"));
        Product saved = savedProduct("p1", "SKU-1", "Widget", new BigDecimal("9.99"));
        when(productRepository.save(toSave)).thenReturn(saved);

        Product result = productService.create(toSave);

        assertThat(result).isEqualTo(saved);
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.PRODUCT_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.PRODUCT_CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(payload).containsEntry("productId", "p1").containsEntry("sku", "SKU-1");
        assertThat(EventContracts.missingFields(EventTypes.PRODUCT_CREATED, payload)).isEmpty();
    }

    @Test
    void update_found_publishesProductUpdated() {
        Product patch = new Product("SKU-1", "Widget v2", new BigDecimal("12.50"));
        Product updated = savedProduct("p1", "SKU-1", "Widget v2", new BigDecimal("12.50"));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("system"));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Product.class)))
                .thenReturn(updated);

        Product result = productService.update("p1", patch);

        assertThat(result).isEqualTo(updated);
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.PRODUCT_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.PRODUCT_UPDATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(EventContracts.missingFields(EventTypes.PRODUCT_UPDATED, payload)).isEmpty();
    }

    @Test
    void update_notFound_throwsNotFoundAndPublishesNothing() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("system"));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Product.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> productService.update("missing", new Product("SKU-1", "Widget", BigDecimal.TEN)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void delete_found_marksDeletedAndPublishesProductDeleted() {
        Product product = savedProduct("p1", "SKU-1", "Widget", BigDecimal.TEN);
        when(productRepository.findByIdAndDeletedFalse("p1")).thenReturn(Optional.of(product));

        productService.delete("p1");

        assertThat(product.isDeleted()).isTrue();
        verify(productRepository).save(product);
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.PRODUCT_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.PRODUCT_DELETED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(EventContracts.missingFields(EventTypes.PRODUCT_DELETED, payload)).isEmpty();
    }

    @Test
    void delete_notFound_throwsNotFoundAndPublishesNothing() {
        when(productRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete("missing")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(kafkaTemplate);
        verify(productRepository, never()).save(any());
    }
}
