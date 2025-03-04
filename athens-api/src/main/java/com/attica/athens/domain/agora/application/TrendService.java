package com.attica.athens.domain.agora.application;

import static com.attica.athens.domain.agora.domain.TrendWeight.CHAT_WEIGHT;
import static com.attica.athens.domain.agora.domain.TrendWeight.COUNT_MULTIPLIER;
import static com.attica.athens.domain.agora.domain.TrendWeight.DEFAULT_METRIC_COUNT;
import static com.attica.athens.domain.agora.domain.TrendWeight.HOUR_INTERVAL;
import static com.attica.athens.domain.agora.domain.TrendWeight.INVERSE_BASE;
import static com.attica.athens.domain.agora.domain.TrendWeight.MEMBER_WEIGHT;
import static com.attica.athens.domain.agora.domain.TrendWeight.MIN_CHAT_COUNT;
import static com.attica.athens.domain.agora.domain.TrendWeight.MIN_MEMBER_COUNT;
import static com.attica.athens.domain.agora.domain.TrendWeight.ZERO_VALUE;

import com.attica.athens.domain.agora.dao.AgoraRepository;
import com.attica.athens.domain.agora.dao.PopularRepository;
import com.attica.athens.domain.agora.domain.Agora;
import com.attica.athens.domain.agora.domain.Trend;
import com.attica.athens.domain.agora.dto.AgoraMetrics;
import com.attica.athens.domain.agora.dto.SimpleAgoraResult;
import com.attica.athens.domain.agora.exception.NotFoundAgoraException;
import com.attica.athens.global.redis.service.ChatTracker;
import com.attica.athens.global.redis.service.MemberTracker;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendService {

    private final AgoraRepository agoraRepository;
    private final MemberTracker memberTracker;
    private final ChatTracker chatTracker;

    public int getChatCount(Long agoraId) {
        LocalDateTime now = LocalDateTime.now();
        return chatTracker.getCount(agoraId, now);
    }

    public List<SimpleAgoraResult> getTrendAgora() {
        LocalDateTime now = LocalDateTime.now();

        Set<Long> top10IdsByMembersCount = memberTracker.getTop10IdsByMembersCount();
        Set<Long> top10IdsByChatCount = chatTracker.getTop10IdsByChatCount(now);

        Set<Long> agoraIds = new HashSet<>(top10IdsByMembersCount);
        agoraIds.addAll(top10IdsByChatCount);

        List<AgoraMetrics> agoras = agoraIds
                .stream()
                .map(id -> {
                    long membersCount = memberTracker.getCount(id);
                    long chatCount = chatTracker.getCount(id, now);
                    return new AgoraMetrics(id, membersCount, chatCount);
                })
                .filter(metrics -> metrics.membersCount() >= 1 || metrics.chatCount() >= 1)
                .toList();

        Map<AgoraMetrics, Double> scores = getAgoraScore(agoras);
        double maxScore = getMaxScore(agoras, scores);
        normalizedScore(scores, maxScore);

        List<Long> ids = scores.entrySet().stream()
                .sorted(Entry.comparingByValue())
                .map(element -> element.getKey().agoraId())
                .limit(10)
                .toList();

        return agoraRepository.findAgoraByIdsWithRunning(ids);
    }

    private void normalizedScore(Map<AgoraMetrics, Double> scores, double maxScore) {
        for (Entry<AgoraMetrics, Double> entry : scores.entrySet()) {
            double normalizedScore = (maxScore != ZERO_VALUE.getValue()) ? entry.getValue() / maxScore : ZERO_VALUE.getValue();
            scores.replace(entry.getKey(), normalizedScore);
        }
    }

    private double getMaxScore(List<AgoraMetrics> agoras, Map<AgoraMetrics, Double> scores) {
        final long maxMembersCount = getMaxMemberCount(agoras);
        final long maxChatCount = getMaxChatCount(agoras);

        final double maxMembersCountInverse = (maxMembersCount != ZERO_VALUE.getValue()) ?
                INVERSE_BASE.getValue() / maxMembersCount : ZERO_VALUE.getValue();

        final double maxChatCountInverse = (maxChatCount != ZERO_VALUE.getValue()) ?
                INVERSE_BASE.getValue() / maxChatCount : ZERO_VALUE.getValue();

        return calculateScoreAndMaxScore(scores, maxMembersCountInverse, maxChatCountInverse);
    }

    private double calculateScoreAndMaxScore(Map<AgoraMetrics, Double> scores, double maxMembersCountInverse, double maxChatCountInverse) {
        return scores.entrySet().stream()
                .mapToDouble(entry -> {
                    double originalValue = entry.getValue();
                    long agoraMembersCount = (long) (originalValue / COUNT_MULTIPLIER.getValue());
                    long agoraChatCount = (long) (originalValue % COUNT_MULTIPLIER.getValue());

                    double normalizedMembers = agoraMembersCount * maxMembersCountInverse;
                    double normalizedChats = agoraChatCount * maxChatCountInverse;

                    double score = (MEMBER_WEIGHT.getValue() * normalizedMembers) + (CHAT_WEIGHT.getValue() * normalizedChats);;
                    entry.setValue(score);

                    return score;
                })
                .max()
                .orElse(ZERO_VALUE.getValue());
    }

    private Map<AgoraMetrics, Double> getAgoraScore(List<AgoraMetrics> agoras) {
        return agoras.stream()
                .collect(
                        Collectors.toMap(
                                agora -> agora,
                                agora -> (double) agora.membersCount() * COUNT_MULTIPLIER.getValue()));
    }

    private long getMaxMemberCount(List<AgoraMetrics> agoras) {
        return agoras.stream()
                .max(Comparator.comparingLong(AgoraMetrics::membersCount))
                .map(AgoraMetrics::membersCount)
                .orElse((int) DEFAULT_METRIC_COUNT.getValue());
    }

    private long getMaxChatCount(List<AgoraMetrics> agoras) {
        return agoras.stream()
                .max(Comparator.comparingLong(AgoraMetrics::chatCount))
                .map(AgoraMetrics::chatCount)
                .orElse((int) DEFAULT_METRIC_COUNT.getValue());
    }
}
