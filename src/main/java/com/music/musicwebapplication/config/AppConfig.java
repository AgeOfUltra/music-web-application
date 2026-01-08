package com.music.musicwebapplication.config;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class AppConfig {

    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${cloud.aws.region.static}") //180
    private String region;


    @Value("${aws.s3.api-call-timeout}")
    private int apiCallTimeout;

    @Value("${aws.s3.api-attempt-timeout}") //120
    private int apiAttemptTimeout;

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

    @Bean
    public RetryTemplate uploadRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Retry policy: 5 attempts
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(5);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Backoff policy: exponential backoff
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(2000);  // 2 seconds
        backOffPolicy.setMultiplier(2.0);        // Double each time
        backOffPolicy.setMaxInterval(30000);     // Max 30 seconds
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
