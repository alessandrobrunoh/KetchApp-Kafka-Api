package com.kafka.alessandro_alessandra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfig {

    private final int POOL_SIZE = 5;
    private final String THREAD_NAME_PREFIX = "EmailScheduler-";

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix(THREAD_NAME_PREFIX);
        scheduler.initialize();
        return scheduler;
    }
}
