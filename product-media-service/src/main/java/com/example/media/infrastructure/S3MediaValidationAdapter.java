package com.example.media.infrastructure;

import com.example.media.ports.MediaValidationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import java.io.IOException;
import java.io.UncheckedIOException;

@Component
public class S3MediaValidationAdapter implements MediaValidationPort {

    private final S3Client s3Client;
    private final String stagingBucketName;
    private final String productionBucketName;
    private final String region;

    public S3MediaValidationAdapter(@Value("${spring.cloud.aws.region.static}") String region,
                                     @Value("${aws.s3.staging-bucket-name}") String stagingBucketName,
                                     @Value("${aws.s3.bucket-name}") String productionBucketName) {
        this.region = region;
        this.stagingBucketName = stagingBucketName;
        this.productionBucketName = productionBucketName;
        this.s3Client = S3Client.builder().region(Region.of(region)).build();
    }

    @Override
    public byte[] readStagingObjectHeader(String stagingKey, int maxBytes) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(stagingBucketName).key(stagingKey)
                .range("bytes=0-" + (maxBytes - 1))
                .build();
        try (ResponseInputStream<GetObjectResponse> in = s3Client.getObject(request)) {
            return in.readNBytes(maxBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read staging object header for " + stagingKey, e);
        }
    }

    @Override
    public long stagingObjectSize(String stagingKey) {
        HeadObjectRequest request = HeadObjectRequest.builder().bucket(stagingBucketName).key(stagingKey).build();
        return s3Client.headObject(request).contentLength();
    }

    @Override
    public String copyToProduction(String stagingKey) {
        CopyObjectRequest request = CopyObjectRequest.builder()
                .sourceBucket(stagingBucketName).sourceKey(stagingKey)
                .destinationBucket(productionBucketName).destinationKey(stagingKey)
                .build();
        s3Client.copyObject(request);
        return String.format("https://%s.s3.%s.amazonaws.com/%s", productionBucketName, region, stagingKey);
    }

    @Override
    public void deleteStagingObject(String stagingKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(stagingBucketName).key(stagingKey).build());
    }
}
