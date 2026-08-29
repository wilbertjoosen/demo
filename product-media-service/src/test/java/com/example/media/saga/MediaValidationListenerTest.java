package com.example.media.saga;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.media.ports.MediaValidationPort;
import com.example.media.service.MediaService;
import com.example.media.validation.MediaContentValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaValidationListenerTest {

    @Mock
    MediaValidationPort mediaValidationPort;
    @Mock
    MediaService mediaService;
    @Mock
    MeterRegistry meterRegistry;
    @Mock
    Counter counter;

    MediaValidationListener listener;

    @BeforeEach
    void setUp() {
        listener = new MediaValidationListener(mediaValidationPort, new MediaContentValidator(), mediaService, meterRegistry);
        lenient().when(meterRegistry.counter(any())).thenReturn(counter);
    }

    private static DomainEvent mediaStaged(String mediaAssetId, String key, String contentType) {
        return DomainEvent.of(EventTypes.MEDIA_STAGED, null,
                Map.of("mediaAssetId", mediaAssetId, "stagingKey", key, "contentType", contentType));
    }

    @Test
    void realJpegBytes_promotedToProductionAndActivated() {
        when(mediaValidationPort.stagingObjectSize("media-uploads/x.jpg")).thenReturn(1000L);
        when(mediaValidationPort.readStagingObjectHeader(eq("media-uploads/x.jpg"), anyInt()))
                .thenReturn(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
        when(mediaValidationPort.copyToProduction("media-uploads/x.jpg")).thenReturn("https://production/x.jpg");

        listener.onMediaEvent(mediaStaged("m1", "media-uploads/x.jpg", "image/jpeg"));

        verify(mediaValidationPort).deleteStagingObject("media-uploads/x.jpg");
        verify(mediaService).activate("m1", "https://production/x.jpg");
        verify(mediaService, never()).reject(any());
    }

    @Test
    void spoofedFile_bytesDontMatchExtension_rejected() {
        when(mediaValidationPort.stagingObjectSize("media-uploads/x.jpg")).thenReturn(1000L);
        // MZ (Windows executable) header, not a real JPEG, despite the .jpg key
        when(mediaValidationPort.readStagingObjectHeader(eq("media-uploads/x.jpg"), anyInt()))
                .thenReturn(new byte[]{0x4D, 0x5A, 0x00, 0x00});

        listener.onMediaEvent(mediaStaged("m1", "media-uploads/x.jpg", "image/jpeg"));

        verify(mediaValidationPort).deleteStagingObject("media-uploads/x.jpg");
        verify(mediaService).reject("m1");
        verify(mediaValidationPort, never()).copyToProduction(any());
        verify(mediaService, never()).activate(any(), any());
    }

    @Test
    void emptyObject_rejectedWithoutReadingHeader() {
        when(mediaValidationPort.stagingObjectSize("media-uploads/x.jpg")).thenReturn(0L);

        listener.onMediaEvent(mediaStaged("m1", "media-uploads/x.jpg", "image/jpeg"));

        verify(mediaService).reject("m1");
        verify(mediaValidationPort, never()).readStagingObjectHeader(any(), anyInt());
    }

    @Test
    void disallowedExtension_rejectedWithoutReadingHeader() {
        listener.onMediaEvent(mediaStaged("m1", "media-uploads/x.exe", "application/octet-stream"));

        verify(mediaService).reject("m1");
        verify(mediaValidationPort, never()).readStagingObjectHeader(any(), anyInt());
        verify(mediaValidationPort, never()).stagingObjectSize(any());
    }

    @Test
    void nonMediaStagedEvent_ignored() {
        listener.onMediaEvent(DomainEvent.of(EventTypes.PRODUCT_CREATED, null, Map.of("productId", "p1")));

        verify(mediaService, never()).activate(any(), any());
        verify(mediaService, never()).reject(any());
    }
}
