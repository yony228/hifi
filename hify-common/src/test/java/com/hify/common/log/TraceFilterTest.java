package com.hify.common.log;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link TraceFilter} 的 traceId 生成、MDC 注入/清理、响应头和行为。
 */
@DisplayName("TraceFilter — 请求链路追踪")
class TraceFilterTest {

    private TraceFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TraceFilter();
        MDC.clear(); // 清理前序测试残留
    }

    @Nested
    @DisplayName("traceId 生成")
    class TraceIdGeneration {

        @Test
        @DisplayName("traceId 长度 = 8（UUID 前 8 位）")
        void traceIdLength() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/chat");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilterInternal(req, res, (request, response) -> {
                String traceId = MDC.get(TraceFilter.MDC_KEY);
                assertThat(traceId).hasSize(8);
            });

            assertThat(MDC.get(TraceFilter.MDC_KEY)).isNull(); // 已清理
        }

        @Test
        @DisplayName("traceId 仅包含十六进制字符")
        void traceIdIsHex() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/chat");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilterInternal(req, res, (request, response) -> {
                String traceId = MDC.get(TraceFilter.MDC_KEY);
                assertThat(traceId).matches("[0-9a-f]{8}");
            });
        }

        @Test
        @DisplayName("连续两次请求 traceId 不同")
        void traceIdsAreDifferent() throws Exception {
            String id1, id2;

            MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/v1/chat");
            MockHttpServletResponse res1 = new MockHttpServletResponse();
            filter.doFilterInternal(req1, res1, (rq, rs) -> {
                MDC.get(TraceFilter.MDC_KEY);
            });
            id1 = res1.getHeader(TraceFilter.RESPONSE_HEADER);

            MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/v1/chat");
            MockHttpServletResponse res2 = new MockHttpServletResponse();
            filter.doFilterInternal(req2, res2, (rq, rs) -> {
                MDC.get(TraceFilter.MDC_KEY);
            });
            id2 = res2.getHeader(TraceFilter.RESPONSE_HEADER);

            assertThat(id1).isNotNull();
            assertThat(id2).isNotNull();
            assertThat(id1).isNotEqualTo(id2);
        }
    }

    @Nested
    @DisplayName("MDC 注入与清理")
    class MdcInjection {

        @Test
        @DisplayName("请求处理期间 MDC 包含 traceId")
        void mdcContainsTraceIdDuringRequest() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/agent");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilterInternal(req, res, (request, response) -> {
                assertThat(MDC.get(TraceFilter.MDC_KEY)).isNotNull();
                assertThat(MDC.get(TraceFilter.MDC_KEY)).hasSize(8);
            });
        }

        @Test
        @DisplayName("请求结束后 MDC 已清理")
        void mdcClearedAfterRequest() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/agent");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilterInternal(req, res, (request, response) -> {});

            assertThat(MDC.get(TraceFilter.MDC_KEY)).isNull();
        }

        @Test
        @DisplayName("即使业务抛出异常，MDC 也会被清理")
        void mdcClearedEvenOnException() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/chat");
            MockHttpServletResponse res = new MockHttpServletResponse();

            try {
                filter.doFilterInternal(req, res, (request, response) -> {
                    throw new RuntimeException("模拟业务异常");
                });
            } catch (RuntimeException e) {
                // expected
            }

            assertThat(MDC.get(TraceFilter.MDC_KEY)).isNull();
        }
    }

    @Nested
    @DisplayName("响应头")
    class ResponseHeader {

        @Test
        @DisplayName("响应包含 X-Trace-Id 头")
        void responseContainsTraceIdHeader() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/chat");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilterInternal(req, res, (request, response) -> {});

            assertThat(res.getHeader(TraceFilter.RESPONSE_HEADER)).isNotNull();
            assertThat(res.getHeader(TraceFilter.RESPONSE_HEADER)).hasSize(8);
        }

        @Test
        @DisplayName("X-Trace-Id 值与 MDC 中的值一致")
        void headerMatchesMdc() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/chat");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilterInternal(req, res, (request, response) -> {
                String mdcId = MDC.get(TraceFilter.MDC_KEY);
                assertThat(res.getHeader(TraceFilter.RESPONSE_HEADER)).isEqualTo(mdcId);
            });
        }
    }

    @Nested
    @DisplayName("拦截范围")
    class FilterScope {

        @Test
        @DisplayName("/api/v1/chat → 被拦截（shouldNotFilter = false）")
        void apiPathsAreFiltered() {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/chat");
            assertThat(filter.shouldNotFilter(req)).isFalse();
        }

        @Test
        @DisplayName("/api/v1/agent → 被拦截")
        void apiAgentPathIsFiltered() {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/agent");
            assertThat(filter.shouldNotFilter(req)).isFalse();
        }

        @Test
        @DisplayName("/actuator/health → 不过滤")
        void actuatorIsNotFiltered() {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
            assertThat(filter.shouldNotFilter(req)).isTrue();
        }

        @Test
        @DisplayName("/h2-console → 不过滤")
        void h2ConsoleIsNotFiltered() {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/h2-console");
            assertThat(filter.shouldNotFilter(req)).isTrue();
        }

        @Test
        @DisplayName("null path → 不过滤，不抛异常")
        void nullPathIsNotFiltered() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI(null);
            assertThat(filter.shouldNotFilter(req)).isTrue();
        }
    }
}
