package com.hify.model.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link RateLimitHandler} 的 Retry-After 头解析逻辑，
 * 按 {@code docs/specs/design/llm-invoke-spec.md §5.3} 规则运行。
 */
@DisplayName("RateLimitHandler — 429 Retry-After 解析")
class RateLimitHandlerTest {

    private RateLimitHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RateLimitHandler();
    }

    @Nested
    @DisplayName("Delta Seconds 格式（纯数字）")
    class DeltaSeconds {

        @Test
        @DisplayName("\"120\" → 等待 120s")
        void shouldParseDeltaSeconds() {
            HttpClientErrorException.TooManyRequests ex = tooManyRequestsWithRetryAfter("120");

            Duration wait = handler.extractRetryAfter(ex);

            assertThat(wait).isEqualTo(Duration.ofSeconds(120));
        }

        @Test
        @DisplayName("\"5\" → 等待 5s")
        void shouldParseSmallDelta() {
            HttpClientErrorException.TooManyRequests ex = tooManyRequestsWithRetryAfter("5");

            Duration wait = handler.extractRetryAfter(ex);

            assertThat(wait).isEqualTo(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("\"3600\" → 上限截断为 120s")
        void shouldCapAtMaxWait() {
            HttpClientErrorException.TooManyRequests ex = tooManyRequestsWithRetryAfter("3600");

            Duration wait = handler.extractRetryAfter(ex);

            assertThat(wait).isEqualTo(Duration.ofSeconds(120));
        }
    }

    @Nested
    @DisplayName("HTTP-date 格式")
    class HttpDate {

        @Test
        @DisplayName("未来时间 → 正等待时长")
        void shouldParseFutureDate() {
            // 当前时间 + 30s 的 RFC 1123 格式
            String futureDate = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                    .format(java.time.ZonedDateTime.now().plusSeconds(30));
            HttpClientErrorException.TooManyRequests ex = tooManyRequestsWithRetryAfter(futureDate);

            Duration wait = handler.extractRetryAfter(ex);

            assertThat(wait.getSeconds()).isBetween(1L, 30L);
        }

        @Test
        @DisplayName("过去时间 → Duration.ZERO")
        void shouldReturnZeroForPastDate() {
            String pastDate = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                    .format(java.time.ZonedDateTime.now().minusHours(1));
            HttpClientErrorException.TooManyRequests ex = tooManyRequestsWithRetryAfter(pastDate);

            Duration wait = handler.extractRetryAfter(ex);

            assertThat(wait).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("无效格式 → 默认 15s")
        void shouldReturnDefaultForInvalidFormat() {
            HttpClientErrorException.TooManyRequests ex = tooManyRequestsWithRetryAfter("not-a-valid-value");

            Duration wait = handler.extractRetryAfter(ex);

            assertThat(wait).isEqualTo(Duration.ofSeconds(15));
        }
    }

    @Nested
    @DisplayName("无 Retry-After 头")
    class MissingHeader {

        @Test
        @DisplayName("headers 中无 Retry-After → 默认 15s")
        void shouldReturnDefaultWhenMissing() {
            HttpClientErrorException.TooManyRequests ex = tooManyRequestsWithRetryAfter(null);

            Duration wait = handler.extractRetryAfter(ex);

            assertThat(wait).isEqualTo(Duration.ofSeconds(15));
        }

        @Test
        @DisplayName("空字符串 → 默认 15s")
        void shouldReturnDefaultForEmptyValue() {
            HttpClientErrorException.TooManyRequests ex = tooManyRequestsWithRetryAfter("");

            Duration wait = handler.extractRetryAfter(ex);

            assertThat(wait).isEqualTo(Duration.ofSeconds(15));
        }
    }

    // ======================== helper ========================

    private static HttpClientErrorException.TooManyRequests tooManyRequestsWithRetryAfter(String retryAfter) {
        HttpHeaders headers = new HttpHeaders();
        if (retryAfter != null) {
            headers.set("Retry-After", retryAfter);
        }
        HttpClientErrorException.TooManyRequests ex =
                mock(HttpClientErrorException.TooManyRequests.class);
        when(ex.getResponseHeaders()).thenReturn(headers);
        return ex;
    }
}
