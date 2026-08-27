package com.site.xidong.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * [캐시] QuestionSet 목록처럼 거의 안 바뀌는데 매 요청마다 DB를 긁는 값을 Redis에 캐싱한다.
 * Redis Pub/Sub(RedisConfig) 작업에서 이미 붙인 RedisConnectionFactory를 그대로 재사용 —
 * 인프라를 공유하니 이 캐시 하나 추가하는 데 새 설정이 거의 안 든다.
 *
 * [캐시 스탬피드] TTL 10분으로 인스턴스마다 독립적으로 만료되므로, 여러 인스턴스가
 * 정확히 같은 순간에 캐시 미스를 일으킬 확률 자체가 낮다. 게다가 동시 트래픽 규모가
 * 크지 않은 지금 단계에서는 캐시 미스 시 DB로 몇 개 요청이 겹쳐 들어가도(최악의 경우
 * findAllOpenQuestionSetsWithQuestions() 몇 번 중복 실행) 감당 못 할 부하가 아니다.
 * 같은 인스턴스 안에서 여러 스레드가 동시에 미스를 내는 경우는 @Cacheable(sync = true)로
 * 이미 막아뒀다(뒤에서 온 스레드는 첫 스레드가 채운 값을 그대로 받음) — 공짜로 얻을 수
 * 있는 부분은 챙기고, 여러 인스턴스가 동시에 미스 내는 진짜 분산 스탬피드 방지(분산 락 등)는
 * 트래픽이 커지기 전까지는 굳이 들일 비용이 아니라고 판단해 의도적으로 뺐다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper())));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    // [버그 발견] GenericJackson2JsonRedisSerializer()의 무인자 생성자는 자체 ObjectMapper를
    // 새로 만드는데, 앱 전역에서 쓰는(JavaTimeModule이 등록된) 빈과는 별개라 LocalDateTime
    // 필드(QuestionSetReturnDTO.createdAt 등)를 만나면 "Java 8 date/time type... not supported"로
    // 캐시 쓰기 자체가 예외로 죽는 걸 실제로 캐시를 태워보고서야 발견했다.
    //
    // 앱 전역 ObjectMapper 빈을 그대로 재사용하지 않는 이유: GenericJackson2JsonRedisSerializer가
    // 제네릭 타입을 복원하려면 activateDefaultTyping으로 "@class" 메타데이터를 넣어야 하는데,
    // 이건 그 ObjectMapper로 직렬화되는 모든 JSON에 영향을 주는 전역 설정이다. 앱 전역 빈에
    // 걸면 REST API 응답 JSON에도 "@class"가 섞여 나가 프론트가 깨진다 — 그래서 Redis 캐시
    // 전용으로 완전히 분리된 ObjectMapper를 새로 만든다.
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
        return mapper;
    }
}
