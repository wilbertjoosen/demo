package com.example.media.dto;

import java.util.List;

/**
 * Exactly one of the two shapes is populated, decided server-side by file size:
 * {@code uploadUrl} alone for a single-PUT upload, or {@code uploadId}/{@code parts} for a
 * multipart one. The frontend switches on which fields are present rather than being told the mode
 * directly — see PresignedMediaUploadService for the size threshold.
 */
public record PresignedUploadResponse(
        String key,
        String uploadUrl,
        String uploadId,
        List<PresignedPart> parts,
        Long partSizeBytes) {

    public static PresignedUploadResponse singlePart(String key, String uploadUrl) {
        return new PresignedUploadResponse(key, uploadUrl, null, null, null);
    }

    public static PresignedUploadResponse multipart(String key, String uploadId, List<PresignedPart> parts, long partSizeBytes) {
        return new PresignedUploadResponse(key, null, uploadId, parts, partSizeBytes);
    }

    public record PresignedPart(int partNumber, String uploadUrl) {
    }
}
