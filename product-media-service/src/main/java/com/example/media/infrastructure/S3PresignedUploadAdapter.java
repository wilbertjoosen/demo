package com.example.media.infrastructure;

import com.example.media.ports.PresignedUploadPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.ListMultipartUploadsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.util.List;

/**
 * All uploads through this adapter target the staging bucket, never production — see
 * MediaValidationPort/MediaValidationListener for the only path an object can take from there to
 * production. CreateMultipartUpload/CompleteMultipartUpload/AbortMultipartUpload go through the
 * real {@link S3Client} directly (cheap metadata-only calls, no file bytes) rather than being
 * presigned themselves — only presignUploadPart needs to bypass this service, since that's the one
 * call that actually carries a chunk of the file.
 */
@Component
public class S3PresignedUploadAdapter implements PresignedUploadPort {

    private final S3Presigner presigner;
    private final S3Client s3Client;
    private final String stagingBucketName;

    public S3PresignedUploadAdapter(@Value("${spring.cloud.aws.region.static}") String region,
                                     @Value("${aws.s3.staging-bucket-name}") String stagingBucketName) {
        this.stagingBucketName = stagingBucketName;
        this.presigner = S3Presigner.builder().region(Region.of(region)).build();
        this.s3Client = S3Client.builder().region(Region.of(region)).build();
    }

    @Override
    public URL presignPutObject(String key, String contentType, Duration expiry) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(stagingBucketName).key(key).contentType(contentType).build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiry).putObjectRequest(putObjectRequest).build();
        return presigner.presignPutObject(presignRequest).url();
    }

    @Override
    public String createMultipartUpload(String key, String contentType) {
        CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                .bucket(stagingBucketName).key(key).contentType(contentType).build();
        return s3Client.createMultipartUpload(request).uploadId();
    }

    @Override
    public URL presignUploadPart(String key, String uploadId, int partNumber, Duration expiry) {
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(stagingBucketName).key(key).uploadId(uploadId).partNumber(partNumber).build();
        UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                .signatureDuration(expiry).uploadPartRequest(uploadPartRequest).build();
        return presigner.presignUploadPart(presignRequest).url();
    }

    @Override
    public String completeMultipartUpload(String key, String uploadId, List<PartETag> parts) {
        List<CompletedPart> completedParts = parts.stream()
                .map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.eTag()).build())
                .toList();
        CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                .bucket(stagingBucketName).key(key).uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                .build();
        return s3Client.completeMultipartUpload(request).location();
    }

    @Override
    public void abortMultipartUpload(String key, String uploadId) {
        s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(stagingBucketName).key(key).uploadId(uploadId).build());
    }

    @Override
    public List<IncompleteUpload> listIncompleteUploads() {
        ListMultipartUploadsRequest request = ListMultipartUploadsRequest.builder().bucket(stagingBucketName).build();
        return s3Client.listMultipartUploads(request).uploads().stream()
                .map(upload -> new IncompleteUpload(upload.key(), upload.uploadId(), upload.initiated()))
                .toList();
    }
}
