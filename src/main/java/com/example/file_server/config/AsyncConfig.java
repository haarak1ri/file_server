package com.example.file_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);      // потоков всегда готовы
        executor.setMaxPoolSize(20);      // максимум потоков
        executor.setQueueCapacity(100);   // очередь задач
        executor.setThreadNamePrefix("streaming-");
        executor.initialize();
        return executor;
    }
}