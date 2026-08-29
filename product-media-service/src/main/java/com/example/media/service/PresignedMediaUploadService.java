package com.example.media.service;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.media.dto.CompleteMultipartUploadRequest;
import com.example.media.dto.ConfirmStagedUploadRequest;
import com.example.media.dto.PresignedUploadRequest;
import com.example.media.dto.PresignedUploadResponse;
import com.example.media.dto.PresignedUploadResponse.PresignedPart;
import com.example.media.model.MediaAsset;
import com.example.media.ports.PresignedUploadPort;
import com.example.media.ports.PresignedUploadPort.PartETag;
import com.example.media.validation.MediaContentValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Decides single-PUT vs. multipart purely by size and hands back whichever shape of presigned
 * URL(s) the frontend needs — see PresignedUploadResponse's javadoc for how it signals which mode
 * it's in. Every upload lands in the staging bucket; MediaValidationListener is what moves it to
 * production after {@link com.example.media.controller.MediaUploadController}'s confirm endpoint
 * publishes MEDIA_STAGED.
 */
@Service
public class PresignedMediaUploadService {

    private static final Duration URL_EXPIRY = Duration.ofMinutes(15);

    private final PresignedUploadPort presignedUploadPort;
    private final MediaService mediaService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String uploadDir;
    private final long multipartThresholdBytes;
    private final long partSizeBytes;
    private final String region;
    private final String stagingBucketName;

    public PresignedMediaUploadService(
            PresignedUploadPort presignedUploadPort,
            MediaService mediaService,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${media.upload-dir}") String uploadDir,
            @Value("${media.presigned-upload.multipart-threshold-bytes:8388608}") long multipartThresholdBytes,
            @Value("${media.presigned-upload.part-size-bytes:5242880}") long partSizeBytes,
            @Value("${spring.cloud.aws.region.static}") String region,
            @Value("${aws.s3.staging-bucket-name}") String stagingBucketName) {
        this.presignedUploadPort = presignedUploadPort;
        this.mediaService = mediaService;
        this.kafkaTemplate = kafkaTemplate;
        this.uploadDir = uploadDir;
        this.multipartThresholdBytes = multipartThresholdBytes;
        this.partSizeBytes = partSizeBytes;
        this.region = region;
        this.stagingBucketName = stagingBucketName;
    }

    public PresignedUploadResponse initiate(PresignedUploadRequest request) {
        String extension = extensionOf(request.fileName());
        if (!MediaContentValidator.ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type: " + extension);
        }
        String key = uploadDir + "/" + UUID.randomUUID() + "." + extension;

        if (request.fileSizeBytes() <= multipartThresholdBytes) {
            String uploadUrl = presignedUploadPort.presignPutObject(key, request.contentType(), URL_EXPIRY).toString();
            return PresignedUploadResponse.singlePart(key, uploadUrl);
        }

        String uploadId = presignedUploadPort.createMultipartUpload(key, request.contentType());
        int partCount = (int) Math.ceil((double) request.fileSizeBytes() / partSizeBytes);
        List<PresignedPart> parts = IntStream.rangeClosed(1, partCount)
                .mapToObj(partNumber -> new PresignedPart(partNumber,
                        presignedUploadPort.presignUploadPart(key, uploadId, partNumber, URL_EXPIRY).toString()))
                .toList();
        return PresignedUploadResponse.multipart(key, uploadId, parts, partSizeBytes);
    }

    public void completeMultipart(CompleteMultipartUploadRequest request) {
        List<PartETag> parts = request.parts().stream()
                .map(p -> new PartETag(p.partNumber(), p.eTag()))
                .toList();
        presignedUploadPort.completeMultipartUpload(request.key(), request.uploadId(), parts);
    }

    public void abortMultipart(String key, String uploadId) {
        presignedUploadPort.abortMultipartUpload(key, uploadId);
    }

    /**
     * Called once the frontend has finished the actual upload (single PUT succeeded, or multipart
     * parts + completeMultipart()) — creates the MediaAsset PENDING_VALIDATION and publishes
     * MEDIA_STAGED so MediaValidationListener picks it up. The asset's own id lets the frontend
     * show "processing" state for something concrete rather than just a bare key string.
     */
    public MediaAsset confirm(ConfirmStagedUploadRequest request) {
        String stagingObjectUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", stagingBucketName, region, request.key());
        MediaAsset asset = mediaService.createPendingValidation(
                request.productId(), request.type(), stagingObjectUrl, request.fileName(), request.caption());
        kafkaTemplate.send(Topics.MEDIA_EVENTS, DomainEvent.of(EventTypes.MEDIA_STAGED, null, Map.of(
                "mediaAssetId", asset.getId(),
                "stagingKey", request.key(),
                "contentType", contentTypeOf(request.fileName()))));
        return asset;
    }

    private String contentTypeOf(String fileName) {
        String extension = extensionOf(fileName);
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mov" -> "video/quicktime";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    private String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File has no extension");
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
