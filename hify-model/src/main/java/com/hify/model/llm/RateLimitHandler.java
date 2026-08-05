package com.hify.model.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * HTTP 429 (Rate Limit) 响应的 {@code Retry-After} 头解析器。
 *
 * <p>对应规范 {@code docs/specs/design/llm-invoke-spec.md §5.3}：</p>
 * <ul>
 *   <li>Delta seconds 格式：{@code "120"} → 等待 120 秒</li>
 *   <li>HTTP-date 格式：{@code "Wed, 21 Oct 2015 07:28:00 GMT"} → 计算时间差</li>
 *   <li>取不到 Retry-After 头时默认等待 15 秒</li>
 *   <li>上限 120 秒防止等待过久</li>
 * </ul>
 */
@Slf4j
@Component
public class RateLimitHandler {

    /** 纯数字格式：单位的秒数。 */
    private static final Pattern DELTA_SECONDS = Pattern.compile("^\\d+$");

    /** 取不到 Retry-After 头时的默认等待。 */
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(15);

    /** 等待上限。 */
    private static final Duration MAX_WAIT = Duration.ofSeconds(120);

    /**
     * 从 HTTP 429 响应中提取应等待的时间。
     *
     * @param ex {@link HttpClientErrorException.TooManyRequests} 异常
     * @return 建议等待的时长；取不到头时返回 15 秒默认值
     */
    public Duration extractRetryAfter(HttpClientErrorException.TooManyRequests ex) {
        List<String> headers = ex.getResponseHeaders().get("Retry-After");
        if (headers == null || headers.isEmpty()) {
            log.debug("429 响应无 Retry-After 头，使用默认等待 {}", DEFAULT_WAIT);
            return DEFAULT_WAIT;
        }

        String value = headers.get(0).trim();

        // 格式一：delta seconds（"120"）
        if (DELTA_SECONDS.matcher(value).matches()) {
            long seconds = Long.parseLong(value);
            Duration wait = Duration.ofSeconds(Math.min(seconds, MAX_WAIT.toSeconds()));
            log.debug("Retry-After delta: {}s → 等待 {}", seconds, wait);
            return wait;
        }

        // 格式二：HTTP-date（RFC 1123）
        try {
            Instant retryTime = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value));
            Duration wait = Duration.between(Instant.now(), retryTime);
            if (wait.isNegative()) {
                return Duration.ZERO;
            }
            Duration capped = wait.compareTo(MAX_WAIT) > 0 ? MAX_WAIT : wait;
            log.debug("Retry-After HTTP-date: {} → 等待 {}", value, capped);
            return capped;
        } catch (DateTimeParseException e) {
            log.debug("Retry-After 格式无法解析: {}，使用默认等待 {}", value, DEFAULT_WAIT);
            return DEFAULT_WAIT;
        }
    }
}
