package com.example.media.saga;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.media.model.MediaAsset;
import com.example.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MediaProvisioningListener {

    private final MediaService mediaService;

    @KafkaListener(topics = Topics.PRODUCT_EVENTS, groupId = "product-media-service")
    public void onProductEvent(DomainEvent event) {
        if (EventTypes.PRODUCT_DELETED.equals(event.eventType())) {
            String productId = (String) ((Map<?, ?>) event.payload()).get("productId");
            List<MediaAsset> productMedia = mediaService.listByProduct(productId);
            productMedia.stream().forEach(mediaAsset -> {
                mediaService.delete(mediaAsset.getId());
            });
        }
    }
}
