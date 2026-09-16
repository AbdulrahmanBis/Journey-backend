package com.journey.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Background execution for work that must not sit in a user's request — currently notification
 * delivery.
 *
 * <p>A bounded pool with a bounded queue on purpose. The default {@code SimpleAsyncTaskExecutor}
 * starts a new thread per call, which under a burst of assignments would happily create thousands.
 * When this queue fills, {@code CallerRunsPolicy} pushes the work back onto the calling thread:
 * notifications get slower, which is the right thing to sacrifice, rather than being dropped.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "applicationTaskExecutor")
    public Executor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("journey-async-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // Let in-flight notifications finish when the app shuts down.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
