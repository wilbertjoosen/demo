package com.example.shipping.repository;

import com.example.shipping.enums.ShipmentStatus;
import com.example.shipping.enums.TrackingStatus;
import com.example.shipping.model.Shipment;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends MongoRepository<Shipment, String> {

    List<Shipment> findByDeletedFalse();

    Optional<Shipment> findByIdAndDeletedFalse(String id);

    Optional<Shipment> findByOrderIdAndDeletedFalse(String orderId);

    List<Shipment> findByStatusAndTrackingStatusNot(ShipmentStatus status, TrackingStatus trackingStatus);
}
