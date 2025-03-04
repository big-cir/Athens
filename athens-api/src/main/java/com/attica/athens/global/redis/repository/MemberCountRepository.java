package com.attica.athens.global.redis.repository;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MemberCountRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_TYPE = "members";

    public void saveWithParticipate(Long agoraId) {
        redisTemplate.opsForZSet().incrementScore(KEY_TYPE, agoraId, 1);
    }

    public void deleteWithExit(Long agoraId) {
        redisTemplate.opsForZSet().incrementScore(KEY_TYPE, agoraId, -1);
    }

    public int countByAgoraId(Long agoraId) {
        Double score = redisTemplate.opsForZSet().score(KEY_TYPE, agoraId);
        return score != null ? score.intValue() : 0;
    }

    public Set<Object> findTop10Members() {
        return redisTemplate.opsForZSet().reverseRange(KEY_TYPE, 0, 9);
    }

    public void deleteWithEnd(Long agoraId) {
        redisTemplate.opsForZSet().remove(KEY_TYPE, agoraId);
    }
}
