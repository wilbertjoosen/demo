package com.example.media.service;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventContracts;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.media.dto.ConfirmStagedUploadRequest;
import com.example.media.dto.PresignedUploadRequest;
import com.example.media.dto.PresignedUploadResponse;
import com.example.media.enums.MediaType;
import com.example.media.model.MediaAsset;
import com.example.media.ports.PresignedUploadPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresignedMediaUploadServiceTest {

    private static final long MULTIPART_THRESHOLD = 8_000_000L;
    private static final long PART_SIZE = 5_000_000L;

    @Mock
    PresignedUploadPort presignedUploadPort;
    @Mock
    MediaService mediaService;
    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    PresignedMediaUploadService service;

    @BeforeEach
    void setUp() {
        service = new PresignedMediaUploadService(presignedUploadPort, mediaService, kafkaTemplate,
                "media-uploads", MULTIPART_THRESHOLD, PART_SIZE, "us-east-1", "staging-demo-wilbert");
    }

    private static URL url(String s) {
        try {
            return URI.create(s).toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void initiate_smallFile_returnsSinglePresignedUrl() {
        when(presignedUploadPort.presignPutObject(any(), eq("image/jpeg"), any())).thenReturn(url("https://s3/put-url"));

        PresignedUploadResponse response = service.initiate(new PresignedUploadRequest("photo.jpg", "image/jpeg", 1_000_000L));

        assertThat(response.uploadUrl()).isEqualTo("https://s3/put-url");
        assertThat(response.uploadId()).isNull();
        assertThat(response.parts()).isNull();
        assertThat(response.key()).startsWith("media-uploads/").endsWith(".jpg");
    }

    @Test
    void initiate_largeFile_returnsMultipartSessionSizedToPartCount() {
        when(presignedUploadPort.createMultipartUpload(any(), any())).thenReturn("upload-123");
        when(presignedUploadPort.presignUploadPart(any(), eq("upload-123"), anyInt(), any()))
                .thenAnswer(inv -> url("https://s3/part-" + inv.getArgument(2)));

        // 12MB at a 5MB part size => 3 parts (5 + 5 + 2)
        PresignedUploadResponse response = service.initiate(new PresignedUploadRequest("video.mp4", "video/mp4", 12_000_000L));

        assertThat(response.uploadUrl()).isNull();
        assertThat(response.uploadId()).isEqualTo("upload-123");
        assertThat(response.parts()).hasSize(3);
        assertThat(response.parts()).extracting(PresignedUploadResponse.PresignedPart::partNumber).containsExactly(1, 2, 3);
        assertThat(response.partSizeBytes()).isEqualTo(PART_SIZE);
    }

    @Test
    void initiate_disallowedExtension_rejectedBeforeTouchingS3() {
        assertThatThrownBy(() -> service.initiate(new PresignedUploadRequest("malware.exe", "application/octet-stream", 1000L)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void confirm_createsPendingAssetAndPublishesContractSatisfyingEvent() {
        String stagingUrl = "https://staging-demo-wilbert.s3.us-east-1.amazonaws.com/media-uploads/x.jpg";
        MediaAsset pending = new MediaAsset("p1", MediaType.PHOTO, stagingUrl, "photo.jpg", "media-uploads", null, 0);
        ReflectionTestUtils.setField(pending, "id", "m1");
        when(mediaService.createPendingValidation(eq("p1"), eq(MediaType.PHOTO), any(), eq("photo.jpg"), any()))
                .thenReturn(pending);
        ConfirmStagedUploadRequest request =
                new ConfirmStagedUploadRequest("p1", MediaType.PHOTO, "media-uploads/x.jpg", "photo.jpg", null);

        MediaAsset result = service.confirm(request);

        assertThat(result).isSameAs(pending);
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.MEDIA_EVENTS), eventCaptor.capture());
        DomainEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(EventTypes.MEDIA_STAGED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.payload();
        assertThat(payload).containsEntry("stagingKey", "media-uploads/x.jpg").containsEntry("contentType", "image/jpeg");
        assertThat(EventContracts.missingFields(EventTypes.MEDIA_STAGED, payload)).isEmpty();
    }
}
