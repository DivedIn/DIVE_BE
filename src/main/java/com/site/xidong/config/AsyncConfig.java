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
        executor.setQueueCapacity(50); // Database as a queue가 큐 역할을 하므로 스레드풀에는 별도로 큐가 필요없음
        executor.setThreadNamePrefix("VideoProcessing-");
        /*
        executor.setRejectedExecutionHandler((r, e) -> {
            log.error("DB 큐 적용했지만 요청 유실이 발생함");
            log.error("Active: {}, Queue: {}", e.getActiveCount(), e.getQueue().size());
        });
         */
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "videoProcessingExecutor")
    public DelegatingSecurityContextAsyncTaskExecutor videoProcessingExecutor(
            @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor executor) {
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    // [병렬화] processVideo()가 duration 확인/썸네일 생성을 fan-out할 때 쓰는 전용 풀.
    // threadPoolTaskExecutor(core=max=10, 큐 50)를 그대로 재사용하면, 동시 처리 중인 영상이
    // 풀 코어 수(10)에 도달하는 순간 워커 스레드 전부가 "자기가 던진 서브태스크"를
    // join()으로 기다리며 블로킹되고, 그 서브태스크는 일할 스레드가 하나도 안 남은 같은 풀의
    // 큐에 갇혀 영원히 못 돌아 데드락이 난다. 그래서 물리적으로 다른 풀을 쓴다.
    @Bean(name = "videoFanOutExecutor")
    public ThreadPoolTaskExecutor videoFanOutExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("VideoFanOut-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("비동기 메서드 예외 발생: method={}, params={}", method.getName(), params, throwable);
        };
    }

}
