package com.example.media.infrastructure;

import com.example.media.ports.StoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3StorageAdapter implements StoragePort {

    private final S3Client s3Client;
    private final String region;
    private final String bucketName;

    public S3StorageAdapter(@Value("${spring.cloud.aws.region.static}") String region,
                            @Value("${aws.s3.bucket-name}") String bucketName) {
        this.region = region;
        this.bucketName = bucketName;
        this.s3Client = S3Client.builder()
                        .region(Region.of(this.region))
                        .build();
    }

    @Override
    public String uploadFile(byte[] fileData, String fileName, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(contentType)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileData));

        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, fileName);
    }

    @Override
    public boolean deleteFile(String fileMame) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(fileMame)
                .build();

        DeleteObjectResponse delete = s3Client.deleteObject(deleteObjectRequest);

        return delete.sdkHttpResponse().isSuccessful();
    }
}
