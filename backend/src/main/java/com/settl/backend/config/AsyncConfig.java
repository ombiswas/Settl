package com.settl.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Value("${app.mail.async.core-pool-size:2}")
    private int corePoolSize;

    @Value("${app.mail.async.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${app.mail.async.queue-capacity:200}")
    private int queueCapacity;

    @Value("${app.mail.async.thread-name-prefix:mail-async-}")
    private String threadNamePrefix;

    @Value("${app.mail.async.synchronous:false}")
    private boolean synchronous;

    @Bean(name = "mailTaskExecutor")
    public Executor mailTaskExecutor() {
        if (synchronous) {
            log.info("Configuring mailTaskExecutor with SyncTaskExecutor");
            return new SyncTaskExecutor();
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler((runnable, exec) -> {
            log.error("Rejected async email execution: queue is full. Core: {}, Max: {}, QueueCapacity: {}, Active: {}, QueueSize: {}",
                    exec.getCorePoolSize(), exec.getMaximumPoolSize(), queueCapacity,
                    exec.getActiveCount(), exec.getQueue().size());
        });
        executor.initialize();
        return executor;
    }
}
