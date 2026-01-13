package com.site.xidong.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "threadPoolTaskExecutor")
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("VideoProcessing-");
        executor.setRejectedExecutionHandler((r, e) -> {
            log.error("DB 큐 적용했지만 요청 유실이 발생함");
            log.error("Active: {}, Queue: {}", e.getActiveCount(), e.getQueue().size());
        });
        executor.initialize();
        return executor;
    }

    @Bean(name = "videoProcessingExecutor")
    public DelegatingSecurityContextAsyncTaskExecutor videoProcessingExecutor(
            @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor executor) {
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("비동기 메서드 예외 발생: method={}, params={}", method.getName(), params, throwable);
        };
    }

}
