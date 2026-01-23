package com.music.musicwebapplication.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@Configuration
@Slf4j
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class AppConfig {

    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${aws.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${cloud.aws.region.static}")
    private String region;


    @Value("${aws.s3.api-call-timeout}")
    private int apiCallTimeout;

    @Value("${aws.s3.api-attempt-timeout}")
    private int apiAttemptTimeout;

    @PostConstruct
    public void validateConfig() {
        log.info("📦 AWS S3 Configuration:");
        log.info("   Bucket: {}", bucketName);
        log.info("   Region: {}", region);
        log.info("   Endpoint: s3.{}.amazonaws.com", region);
    }
    @Bean
    public ModelMapper mapper()
    {
        return new ModelMapper();
    }
    @Bean
    public S3Client s3Client(){
        AwsBasicCredentials awsBasicCredentials = AwsBasicCredentials.create(accessKey,secretKey);
        return  S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsBasicCredentials))
                .overrideConfiguration(cfg->cfg.apiCallTimeout(Duration.ofSeconds(apiCallTimeout))
                        .apiCallAttemptTimeout(Duration.ofSeconds(apiAttemptTimeout)))
                .build();
    }
}
