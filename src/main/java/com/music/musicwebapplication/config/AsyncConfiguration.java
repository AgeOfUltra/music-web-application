package com.music.musicwebapplication.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;


@Configuration
@EnableAsync
@Slf4j
public class AsyncConfiguration implements AsyncConfigurer {


    @Bean(name = "asyncTaskExecutor")
    public Executor asyncTaskExecutor() {
        log.info("🔧 Creating Async Task Executor");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size: Number of threads always kept alive
        executor.setCorePoolSize(5);

        // Max pool size: Maximum number of threads
        executor.setMaxPoolSize(10);

        // Queue capacity: Tasks waiting for execution
        executor.setQueueCapacity(100);

        // Thread name prefix for easy identification in logs
        executor.setThreadNamePrefix("async-cleanup-");

        // Graceful shutdown: wait for tasks to complete
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // Rejection policy: What to do when queue is full
        // CallerRunsPolicy: Run the task in the calling thread
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();

        log.info("✅ Async Task Executor configured: core={}, max={}, queue={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;
    }

    /**
     * Returns the default executor to use for @Async methods without explicit executor name
     */
    @Override
    public Executor getAsyncExecutor() {
        return asyncTaskExecutor();
    }


    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }


    public static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

        @Override
        public void handleUncaughtException(Throwable throwable, Method method, Object... params) {
            log.error("❌ Async method '{}' threw exception: {}",
                    method.getName(),
                    throwable.getMessage(),
                    throwable);

            // Log parameters for debugging
            if (params != null && params.length > 0) {
                log.error("   Parameters: {}", java.util.Arrays.toString(params));
            }
        }
    }
}