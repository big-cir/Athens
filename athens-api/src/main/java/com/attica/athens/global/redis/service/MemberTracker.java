package com.attica.athens.global.redis.service;


import com.attica.athens.global.redis.repository.MemberCountRepository;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberTracker {

    private final MemberCountRepository memberCountRepository;

    public void increase(Long agoraId) {
        memberCountRepository.saveWithParticipate(agoraId);
    }

    public void decrease(Long agoraId) {
        memberCountRepository.deleteWithExit(agoraId);
    }

    public void removeWithEnd(Long agoraId) {
        memberCountRepository.deleteWithEnd(agoraId);
    }

    public int getCount(Long agoraId) {
        return memberCountRepository.countByAgoraId(agoraId);
    }

    public Set<Long> getTop10IdsByMembersCount() {
        return memberCountRepository.findTop10Members().stream()
                .map(this::objToLong)
                .collect(Collectors.toSet());
    }

    private Long objToLong(Object obj) {
        if (obj instanceof Long) return (Long) obj;
        else if (obj instanceof Integer) return ((Integer) obj).longValue();
        else throw new IllegalArgumentException("Unexpected type: " + obj.getClass().getName());
    }
}
