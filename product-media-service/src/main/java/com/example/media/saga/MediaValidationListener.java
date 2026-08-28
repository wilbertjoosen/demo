package com.example.media.saga;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.media.ports.MediaValidationPort;
import com.example.media.service.MediaService;
import com.example.media.validation.MediaContentValidator;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The only place a presigned upload's bytes are actually inspected by this service — everything
 * upstream of this (PresignedMediaUploadService, the frontend PUTting straight to S3) only ever
 * saw the client's claimed filename/Content-Type, never the real content.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MediaValidationListener {

    private final MediaValidationPort mediaValidationPort;
    private final MediaContentValidator contentValidator;
    private final MediaService mediaService;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = Topics.MEDIA_EVENTS, groupId = "product-media-service")
    public void onMediaEvent(DomainEvent event) {
        if (!EventTypes.MEDIA_STAGED.equals(event.eventType())) {
            return;
        }
        Map<?, ?> payload = (Map<?, ?>) event.payload();
        String mediaAssetId = (String) payload.get("mediaAssetId");
        String stagingKey = (String) payload.get("stagingKey");
        String contentType = (String) payload.get("contentType");

        if (isValid(stagingKey, contentType)) {
            String productionUrl = mediaValidationPort.copyToProduction(stagingKey);
            mediaValidationPort.deleteStagingObject(stagingKey);
            mediaService.activate(mediaAssetId, productionUrl);
            meterRegistry.counter("media.validation.accepted").increment();
            log.info("Media validated and promoted to production: mediaAssetId={} key={}", mediaAssetId, stagingKey);
        } else {
            mediaValidationPort.deleteStagingObject(stagingKey);
            mediaService.reject(mediaAssetId);
            meterRegistry.counter("media.validation.rejected").increment();
            log.warn("Media failed content validation, rejected: mediaAssetId={} key={} claimedContentType={}",
                    mediaAssetId, stagingKey, contentType);
        }
    }

    private boolean isValid(String stagingKey, String contentType) {
        String extension = extensionOf(stagingKey);
        if (!MediaContentValidator.ALLOWED_EXTENSIONS.contains(extension)) {
            return false;
        }
        if (mediaValidationPort.stagingObjectSize(stagingKey) <= 0) {
            return false;
        }
        byte[] header = mediaValidationPort.readStagingObjectHeader(stagingKey, MediaContentValidator.HEADER_BYTES_NEEDED);
        return contentValidator.matchesExtension(extension, header);
    }

    private String extensionOf(String key) {
        return key.substring(key.lastIndexOf('.') + 1).toLowerCase();
    }
}
