package com.example.media.enums;

/**
 * Only relevant to assets created through the presigned upload flow — see MediaValidationListener.
 * The old direct-through-the-backend upload (MediaFileStorageService) already has the bytes in this
 * JVM before ever calling MediaService.create(), so those assets skip straight to ACTIVE.
 */
public enum MediaValidationStatus {
    /** Uploaded to the staging bucket, not yet inspected. Not shown in listByProduct(). */
    PENDING_VALIDATION,
    /** Passed validation and copied to the production bucket. */
    ACTIVE,
    /** Failed validation (content doesn't match the claimed type, etc.) — staging object deleted. */
    REJECTED
}
