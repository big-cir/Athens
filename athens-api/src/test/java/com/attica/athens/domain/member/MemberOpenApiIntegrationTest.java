package com.attica.athens.domain.member;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.attica.athens.support.IntegrationTestSupport;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("멤버 오픈 API 통합 테스트")
public class MemberOpenApiIntegrationTest extends IntegrationTestSupport {

    @Nested
    @DisplayName("로컬 회원가입 테스트")
    class LocalSignUpTest {
        @Test
        @DisplayName("로컬에서 멤버를 생성한다.")
        void 성공_멤버생성_유효한파라미터전달() throws Exception {
            // when
            final ResultActions result = mockMvc.perform(
                    post("/{prefix}/open/member", API_V1)
                            .param("username", "testuser")
                            .param("password", "password")
                            .contentType(MediaType.APPLICATION_JSON)
            );

            // then
            result.andExpect(status().isOk())
                    .andExpectAll(
                            jsonPath("$.success").value(true),
                            jsonPath("$.response").exists(),
                            jsonPath("$.response.accessToken").isNotEmpty(),
                            jsonPath("$.response.accessToken").exists(),
                            jsonPath("$.response.accessToken").value(
                                    Matchers.matchesRegex("^[\\w-]+\\.[\\w-]+\\.[\\w-]+$")),
                            jsonPath("$.error").value(nullValue())
                    );
        }
    }

    @Nested
    @DisplayName("토큰 조회 테스트")
    class GetTokenTest {

        @Test
        @DisplayName("토큰을 조회한다.")
        void 성공_토큰조회_유효한액세스토큰() throws Exception {
            // given
            String tempToken = "temp-token";
            String accessToken = "access-token";
            ValueOperations<String, Object> valueOperationsMock = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOperationsMock);
            when(valueOperationsMock.get(tempToken)).thenReturn(accessToken);

            // when
            final ResultActions result = mockMvc.perform(
                    post("/{prefix}/open/member/token", API_V1)
                            .param("temp-token", tempToken)
                            .contentType(MediaType.APPLICATION_JSON)
            );

            // then
            result.andExpect(status().isOk())
                    .andExpectAll(
                            jsonPath("$.access_token").exists(),
                            jsonPath("$.access_token").value(accessToken)
                    );
            verify(valueOperationsMock).get(tempToken);
            verify(redisTemplate).delete(tempToken);
        }
    }

    @Test
    @DisplayName("유효하지 않은 임시 토큰으로 액세스 토큰 조회 시 실패한다.")
    void 실패_토큰조회_유효하지않은액세스토큰() throws Exception {
        // given
        String invalidTempToken = "invalid-temp-token";
        ValueOperations<String, Object> valueOperationsMock = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperationsMock);
        when(valueOperationsMock.get(invalidTempToken)).thenReturn(null);

        // when
        final ResultActions result = mockMvc.perform(
                post("/{prefix}/open/member/token", API_V1)
                        .param("temp-token", invalidTempToken)
                        .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpectAll(
                        jsonPath("$.success").value(false),
                        jsonPath("$.error").exists(),
                        jsonPath("$.error.code").value(1201),
                        jsonPath("$.error.message").value("Invalid temp token"),
                        jsonPath("$.response").doesNotExist()
                );
    }
}
