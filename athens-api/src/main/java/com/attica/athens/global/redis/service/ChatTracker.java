package com.attica.athens.global.redis.service;

import com.attica.athens.global.redis.repository.ChatCountRepository;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatTracker {

    private final ChatCountRepository chatCountRepository;

    public void increase(Long agoraId, LocalDateTime now) {
        chatCountRepository.save(agoraId, now);
    }

    public int getCount(Long agoraId, LocalDateTime now) {
        return chatCountRepository.countByAgoraId(agoraId, now);
    }

    public Set<Long> getTop10IdsByChatCount(LocalDateTime now) {
        return chatCountRepository.findTop10Chats(now).stream()
                .map(this::objToLong)
                .collect(Collectors.toSet());
    }

    private Long objToLong(Object obj) {
        if (obj instanceof Long) return (Long) obj;
        else if (obj instanceof Integer) return ((Integer) obj).longValue();
        else throw new IllegalArgumentException("Unexpected type: " + obj.getClass().getName());
    }
}
