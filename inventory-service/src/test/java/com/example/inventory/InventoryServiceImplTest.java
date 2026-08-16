package com.example.inventory;
import com.example.inventory.model.InventoryItem;
import com.example.inventory.repository.InventoryRepository;
import com.example.inventory.service.InventoryServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    InventoryRepository inventoryRepository;
    @Mock
    MongoTemplate mongoTemplate;
    @Mock
    AuditorAware<String> auditorAware;

    InventoryServiceImpl inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryServiceImpl(inventoryRepository, mongoTemplate, auditorAware);
    }

    @Test
    void reserve_sufficientStock_decrementsAndReturnsTrue() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("system"));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(InventoryItem.class)))
                .thenReturn(new InventoryItem("p1", InventoryItem.DEFAULT_WAREHOUSE, 8));

        assertThat(inventoryService.reserve("p1", 2)).isTrue();
    }

    @Test
    void reserve_insufficientStock_returnsFalse() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("system"));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(InventoryItem.class)))
                .thenReturn(null);

        assertThat(inventoryService.reserve("p1", 999)).isFalse();
    }

    @Test
    void release_incrementsStock() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("system"));

        inventoryService.release("p1", 5);

        verify(mongoTemplate).findAndModify(any(Query.class), any(Update.class), eq(InventoryItem.class));
    }

    @Test
    void aggregateStock_sumsAcrossWarehouses() {
        when(inventoryRepository.findByProductIdAndDeletedFalse("p1")).thenReturn(List.of(
                new InventoryItem("p1", "MAIN", 5), new InventoryItem("p1", "EAST", 3)));

        assertThat(inventoryService.aggregateStock("p1")).isEqualTo(8);
    }

    @Test
    void addStock_upsertsIntoWarehouse() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("system"));
        InventoryItem updated = new InventoryItem("p1", "EAST", 10);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(InventoryItem.class)))
                .thenReturn(updated);

        assertThat(inventoryService.addStock("p1", "EAST", 10)).isEqualTo(updated);
    }

    @Test
    void provisionForNewProduct_notYetProvisioned_createsZeroStockRow() {
        when(inventoryRepository.findByProductIdAndWarehouseId("p1", InventoryItem.DEFAULT_WAREHOUSE)).thenReturn(Optional.empty());

        inventoryService.provisionForNewProduct("p1");

        verify(inventoryRepository).save(any(InventoryItem.class));
    }

    @Test
    void provisionForNewProduct_alreadyProvisioned_isNoOp() {
        when(inventoryRepository.findByProductIdAndWarehouseId("p1", InventoryItem.DEFAULT_WAREHOUSE))
                .thenReturn(Optional.of(new InventoryItem("p1", InventoryItem.DEFAULT_WAREHOUSE, 0)));

        inventoryService.provisionForNewProduct("p1");

        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void delete_found_succeeds() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("system"));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), eq(InventoryItem.class)))
                .thenReturn(new InventoryItem("p1", "MAIN", 0));

        inventoryService.delete("item-1");
    }

    @Test
    void delete_notFound_throwsNotFound() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("system"));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), eq(InventoryItem.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> inventoryService.delete("missing")).isInstanceOf(ResponseStatusException.class);
    }
}
