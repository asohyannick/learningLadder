package com.learningLadder.learningLadder.config.minioConfig;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
@Getter
@Setter
@ConfigurationProperties(prefix = "app.integrations.minio")
public class MinioConfig {

    private String endpoint;
    private int port = 9000;
    private boolean useSsl = false;
    private String region = "us-east-005";
    private String username;
    private String password;
    private String bucketName;
    private int consolePort = 9001;
    private boolean autoCreateBucket = true;

    private String publicEndpoint;

    private int presignedRefreshThresholdHours = 24;

    @Bean
    @Primary
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(host(), port, useSsl)
                .credentials(username, password)
                .region(region)
                .build();
    }

    @Bean("minioPublicClient")
    public MinioClient minioPublicClient() {
        MinioClient.Builder builder = MinioClient.builder()
                .credentials(username, password)
                .region(region);

        if (publicEndpoint == null || publicEndpoint.isBlank()) {
            builder.endpoint(host(), port, useSsl);
        } else {
            builder.endpoint(publicEndpoint.trim());
        }
        return builder.build();
    }

    @Bean
    public ApplicationRunner minioBucketInitializer(MinioClient minioClient) {
        return args -> {
            if (!autoCreateBucket) return;
            try {
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).region(region).build());
                    log.info("MinIO bucket '{}' created", bucketName);
                }
            } catch (Exception ex) {
                log.error("Could not verify or create MinIO bucket '{}': {}", bucketName, ex.getMessage());
            }
        };
    }

    private String host() {
        return endpoint.trim().replaceFirst("^https?://", "").replaceAll("/+$", "");
    }
}