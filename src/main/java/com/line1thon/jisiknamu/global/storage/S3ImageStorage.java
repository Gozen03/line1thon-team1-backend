package com.line1thon.jisiknamu.global.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@ConditionalOnExpression("!'${s3.bucket:}'.isEmpty()")
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration readUrlDuration;

    public S3ImageStorage(@Value("${s3.bucket}") String bucket,
                          @Value("${s3.endpoint:}") String endpoint,
                          @Value("${s3.presigned-url-minutes}") long presignedUrlMinutes,
                          @Value("${aws.region}") String region,
                          @Value("${aws.access-key}") String accessKey,
                          @Value("${aws.secret-key}") String secretKey) {
        this.bucket = bucket;
        this.readUrlDuration = Duration.ofMinutes(presignedUrlMinutes);

        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        // endpoint는 S3 호환 서버로 시험할 때만 쓴다. 비어 있으면 실제 AWS S3로 간다
        var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(!endpoint.isBlank()).build();
        var clientBuilder = S3Client.builder().region(Region.of(region)).credentialsProvider(credentials)
                .serviceConfiguration(pathStyle);
        var presignerBuilder = S3Presigner.builder().region(Region.of(region)).credentialsProvider(credentials)
                .serviceConfiguration(pathStyle);
        if (!endpoint.isBlank()) {
            clientBuilder.endpointOverride(URI.create(endpoint));
            presignerBuilder.endpointOverride(URI.create(endpoint));
        }
        this.s3Client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
    }

    @Override
    public void upload(String key, InputStream content, long size, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromInputStream(content, size));
    }

    @Override
    public String createReadUrl(String key) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(readUrlDuration)
                        .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                        .build())
                .url()
                .toString();
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
