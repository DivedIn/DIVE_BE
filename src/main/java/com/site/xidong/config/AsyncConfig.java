package com.site.xidong.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    // [병렬화] videoFanOutExecutor를 추가하면서 ThreadPoolTaskExecutor 타입 빈이 2개가 됐다.
    // LoadTestController처럼 Lombok @RequiredArgsConstructor + 필드 @Qualifier 조합으로
    // 주입받는 곳은 (이 프로젝트의 Lombok 설정에서는) 그 @Qualifier가 생성자 파라미터까지
    // 전파되지 않아 "bean 2개 발견" 에러로 기동이 아예 안 됐다 — @Autowired 필드 주입은
    // 문제없지만 생성자 주입은 그렇지 않다는 걸 실제로 기동해보고서야 확인했다.
    // 기존에 여러 클래스가 한정자 없이도 "그" 풀을 기대하고 있었을 가능성을 아예 차단하려고
    // 이 빈을 @Primary로 지정해, 한정자를 안 붙인 주입점은 전부 안전하게 여기로 떨어지게 한다.
    @Primary
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
        executor.setTaskDecorator(mdcTaskDecorator());
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
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    // [관측 가능성] @Async 경계를 넘으면 MDC(스레드 로컬)는 기본적으로 새 스레드에 안 딸려온다.
    // 제출하는 쪽 스레드의 MDC를 복사해뒀다가 실행하는 워커 스레드에 그대로 심어줘야
    // traceId가 비동기 처리 로그까지 이어진다. 풀 스레드는 재사용되므로, 실행이 끝나면
    // 반드시 지워야(finally) 다음에 그 스레드가 집는 무관한 작업에 이전 traceId가 새지 않는다.
    private TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> callerContext = MDC.getCopyOfContextMap();
            return () -> {
                if (callerContext != null) {
                    MDC.setContextMap(callerContext);
                }
                try {
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("비동기 메서드 예외 발생: method={}, params={}", method.getName(), params, throwable);
        };
    }

}
