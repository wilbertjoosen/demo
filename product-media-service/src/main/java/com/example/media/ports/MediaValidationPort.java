package com.example.media.ports;

/**
 * Outbound port for what MediaValidationListener needs against the staging/production buckets —
 * deliberately separate from {@link PresignedUploadPort} (generating upload URLs) and
 * {@link StoragePort} (the old whole-file-through-the-backend path): three different concerns,
 * three small interfaces, each with exactly one implementation to swap if this ever needs a second
 * storage backend.
 */
public interface MediaValidationPort {

    /** First {@code maxBytes} of the staging object — enough for magic-byte content sniffing without downloading the whole file. */
    byte[] readStagingObjectHeader(String stagingKey, int maxBytes);

    long stagingObjectSize(String stagingKey);

    /** @return the production bucket URL the object now lives at. */
    String copyToProduction(String stagingKey);

    void deleteStagingObject(String stagingKey);
}
