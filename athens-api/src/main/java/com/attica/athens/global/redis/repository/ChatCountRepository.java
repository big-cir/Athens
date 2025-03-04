package com.attica.athens.global.redis.repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatCountRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_TYPE = "chats:hour";

    public void save(Long agoraId, LocalDateTime now) {
        String key = getKey(now);
        redisTemplate.opsForZSet().incrementScore(key, agoraId, 1);

        long expiredTime = Duration.between(
                now, now.plusHours(1).withMinute(0).withSecond(0).withNano(0))
                .getSeconds();

        keyExpiration(key, expiredTime, TimeUnit.SECONDS);
    }

    public int countByAgoraId(Long agoraId, LocalDateTime now) {
        Double score = redisTemplate.opsForZSet().score(getKey(now), agoraId);
        return score != null ? score.intValue() : 0;
    }

    public Set<Object> findTop10Chats(LocalDateTime now) {
        return redisTemplate.opsForZSet().reverseRange(getKey(now), 0, 9);
    }

    private void keyExpiration(String key, Long expiredTime, TimeUnit timeUnit) {
        redisTemplate.expire(key, expiredTime, timeUnit);
    }

    private String getKey(LocalDateTime now) {
        return KEY_TYPE + ":" + now.getHour();
    }
}
