package com.example.media.service;

import com.example.media.ports.PresignedUploadPort;
import com.example.media.ports.PresignedUploadPort.IncompleteUpload;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbandonedUploadCleanupJobTest {

    @Mock
    PresignedUploadPort presignedUploadPort;
    @Mock
    MeterRegistry meterRegistry;
    @Mock
    Counter counter;

    private AbandonedUploadCleanupJob job(Duration maxAge) {
        AbandonedUploadCleanupJob job = new AbandonedUploadCleanupJob(presignedUploadPort, meterRegistry);
        ReflectionTestUtils.setField(job, "maxAge", maxAge);
        return job;
    }

    @Test
    void uploadOlderThanMaxAge_isAborted() {
        Instant old = Instant.now().minus(Duration.ofHours(25));
        when(presignedUploadPort.listIncompleteUploads()).thenReturn(List.of(new IncompleteUpload("k1", "u1", old)));
        lenient().when(meterRegistry.counter(any())).thenReturn(counter);

        job(Duration.ofHours(24)).abortStaleUploads();

        verify(presignedUploadPort).abortMultipartUpload("k1", "u1");
        verify(counter).increment(1);
    }

    @Test
    void uploadYoungerThanMaxAge_isLeftAlone() {
        Instant recent = Instant.now().minus(Duration.ofHours(1));
        when(presignedUploadPort.listIncompleteUploads()).thenReturn(List.of(new IncompleteUpload("k1", "u1", recent)));

        job(Duration.ofHours(24)).abortStaleUploads();

        verify(presignedUploadPort, never()).abortMultipartUpload(any(), any());
        verify(meterRegistry, never()).counter(any(String.class));
    }

    @Test
    void noIncompleteUploads_doesNothing() {
        when(presignedUploadPort.listIncompleteUploads()).thenReturn(List.of());

        job(Duration.ofHours(24)).abortStaleUploads();

        verify(presignedUploadPort, never()).abortMultipartUpload(any(), any());
    }
}
