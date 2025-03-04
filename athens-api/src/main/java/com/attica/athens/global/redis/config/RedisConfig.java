package com.attica.athens.global.redis.config;

import com.attica.athens.global.properties.RedisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.lettuce.core.RedisConnectionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정 클래스
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class RedisConfig {

    private final RedisProperties redisProperties;

    /**
     * RedisConnectionFactory 빈 등록
     *
     * @return RedisConnectionFactory
     * @throws RedisConnectionException Redis 연결 실패 시 예외
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        try {
            String host = redisProperties.getHost();
            String password = redisProperties.getPassword();
            int port = redisProperties.getPort();

            RedisStandaloneConfiguration factory = new RedisStandaloneConfiguration();
            factory.setHostName(host);
            factory.setPort(port);
            factory.setPassword(password);

            log.debug("Configuring Redis connection to {}:{}", host, port);

            return new LettuceConnectionFactory(factory);
        } catch (Exception e) {
            throw new RedisConnectionException("Failed to create Redis connection factory", e);
        }
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        Jackson2JsonRedisSerializer<Object> jsonSerializer = new Jackson2JsonRedisSerializer<>(Object.class);

        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(jsonSerializer);
        redisTemplate.setHashValueSerializer(jsonSerializer);

        return redisTemplate;
    }
}
