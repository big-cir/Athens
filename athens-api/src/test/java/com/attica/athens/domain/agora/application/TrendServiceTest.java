package com.attica.athens.domain.agora.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.attica.athens.domain.agora.dao.AgoraRepository;
import com.attica.athens.domain.agora.dao.PopularRepository;
import com.attica.athens.global.redis.service.ChatTracker;
import com.attica.athens.global.redis.service.MemberTracker;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrendServiceTest {

    AgoraRepository mockAgoraRepository;
    PopularRepository mockPopularRepository;
    TrendService trendService;
    MemberTracker memberTracker;
    ChatTracker chatTracker;

    @BeforeEach
    void setup() {
        mockAgoraRepository = mock(AgoraRepository.class);
        mockPopularRepository = mock(PopularRepository.class);
        trendService = new TrendService(mockAgoraRepository, memberTracker, chatTracker);
    }

    @Test
    void 성공_스케줄링실행_내부메소드호출() {
        // given & when
//        trendService.calculatePopularAgoraMetrics();
        trendService.getTrendAgora();

        // then
        verify(mockPopularRepository, times(1)).deleteAll();
        verify(mockAgoraRepository, times(1)).findAgoraWithMetricsByDateRange(anyInt(), anyInt(), any(), any());
        verify(mockPopularRepository, times(1)).saveAll(anyList());
    }

    @Test
    void 성공_스케줄링실행_로그출력() {
        // given
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        // when
        // trendService.calculatePopularAgoraMetrics();
        trendService.getTrendAgora();
        String output = outContent.toString();

        // then
        assertTrue(output.contains("스케줄링 작업 시작: calculatePopularAgoraMetrics"));
        assertTrue(output.contains("스케줄링 작업 완료: calculatePopularAgoraMetrics"));
    }
}
