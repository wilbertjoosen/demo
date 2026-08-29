package com.example.media.ports;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbound port for direct-to-S3 uploads — the file bytes never pass through this service at all,
 * unlike {@link StoragePort#uploadFile}, which reads the whole file into this JVM's heap first.
 * CreateMultipartUpload/CompleteMultipartUpload/AbortMultipartUpload are cheap metadata-only S3
 * calls (no bytes), so those go through the real SDK client directly rather than being presigned
 * themselves — only UploadPart (which actually carries a chunk of the file) needs to bypass this
 * service, matching AWS's own recommended pattern for presigned multipart upload.
 */
public interface PresignedUploadPort {

    URL presignPutObject(String key, String contentType, Duration expiry);

    String createMultipartUpload(String key, String contentType);

    URL presignUploadPart(String key, String uploadId, int partNumber, Duration expiry);

    /** @return the completed object's location (URL). */
    String completeMultipartUpload(String key, String uploadId, List<PartETag> parts);

    void abortMultipartUpload(String key, String uploadId);

    /** Every multipart upload session still open in the staging bucket — see AbandonedUploadCleanupJob. */
    List<IncompleteUpload> listIncompleteUploads();

    record PartETag(int partNumber, String eTag) {
    }

    record IncompleteUpload(String key, String uploadId, Instant initiated) {
    }
}
