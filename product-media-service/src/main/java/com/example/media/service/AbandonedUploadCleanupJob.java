package com.example.media.service;

import com.example.media.ports.PresignedUploadPort;
import com.example.media.ports.PresignedUploadPort.IncompleteUpload;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * S3 keeps charging storage for every part already uploaded to an incomplete multipart upload
 * until it's explicitly aborted (or a bucket lifecycle rule does it) — a client that starts a
 * multipart upload and never finishes (closed tab, crashed browser, abandoned form) would
 * otherwise leak storage in the staging bucket forever. common-audit's AuditSchedulingConfig
 * already enables {@code @Scheduled} globally for every service that depends on it, this one
 * included — no additional {@code @EnableScheduling} needed here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AbandonedUploadCleanupJob {

    private final PresignedUploadPort presignedUploadPort;
    private final MeterRegistry meterRegistry;

    @Value("${media.multipart-cleanup.max-age:24h}")
    private Duration maxAge;

    @Scheduled(cron = "${media.multipart-cleanup.cron:0 0 * * * *}")
    public void abortStaleUploads() {
        Instant cutoff = Instant.now().minus(maxAge);
        int abortedCount = 0;
        for (IncompleteUpload upload : presignedUploadPort.listIncompleteUploads()) {
            if (upload.initiated().isBefore(cutoff)) {
                presignedUploadPort.abortMultipartUpload(upload.key(), upload.uploadId());
                log.info("Aborted abandoned multipart upload key={} uploadId={} initiated={}",
                        upload.key(), upload.uploadId(), upload.initiated());
                abortedCount++;
            }
        }
        if (abortedCount > 0) {
            meterRegistry.counter("media.multipart.abandoned.aborted").increment(abortedCount);
        }
    }
}
