package com.hify.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link SseUtil} 的四种 SSE 事件类型和 data 格式。
 */
@DisplayName("SseUtil — SSE 事件工厂")
class SseUtilTest {

    private SseUtil sseUtil;

    @BeforeEach
    void setUp() {
        // SseUtil 需要 JsonUtil，后者需要 ObjectMapper
        // 手工构造——JsonUtil 内部 new ObjectMapper() 无 Spring 上下文也可用
        sseUtil = new SseUtil(new JsonUtil(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    // ======================== token 事件 ========================

    @Nested
    @DisplayName("tokenEvent")
    class TokenEvent {

        @Test
        @DisplayName("event 字段为 \"token\"")
        void eventTypeIsToken() {
            ServerSentEvent<String> event = sseUtil.tokenEvent("你好");

            assertThat(event.event()).isEqualTo("token");
        }

        @Test
        @DisplayName("data 为传入的文本")
        void dataIsText() {
            ServerSentEvent<String> event = sseUtil.tokenEvent("你好，世界");

            assertThat(event.data()).isEqualTo("你好，世界");
        }

        @Test
        @DisplayName("空字符串合法")
        void emptyStringIsValid() {
            ServerSentEvent<String> event = sseUtil.tokenEvent("");

            assertThat(event.data()).isEqualTo("");
            assertThat(event.event()).isEqualTo("token");
        }

        @Test
        @DisplayName("英文 token 正常传递")
        void englishToken() {
            ServerSentEvent<String> event = sseUtil.tokenEvent("Hello");

            assertThat(event.data()).isEqualTo("Hello");
        }
    }

    // ======================== tool_call 事件 ========================

    @Nested
    @DisplayName("toolCallEvent")
    class ToolCallEvent {

        @Test
        @DisplayName("event 字段为 \"tool_call\"")
        void eventTypeIsToolCall() {
            ServerSentEvent<String> event = sseUtil.toolCallEvent("search", Map.of("q", "hello"));

            assertThat(event.event()).isEqualTo("tool_call");
        }

        @Test
        @DisplayName("data 为 JSON，包含 name 和 args")
        void dataContainsNameAndArgs() {
            ServerSentEvent<String> event = sseUtil.toolCallEvent("search",
                    Map.of("query", "天气", "limit", 5));

            assertThat(event.data())
                    .contains("\"name\":\"search\"")
                    .contains("\"query\":\"天气\"")
                    .contains("\"limit\":5");
        }

        @Test
        @DisplayName("args 为 null 时仍可正常序列化")
        void nullArgsIsValid() {
            ServerSentEvent<String> event = sseUtil.toolCallEvent("unknown", null);

            assertThat(event.event()).isEqualTo("tool_call");
            assertThat(event.data())
                    .contains("\"name\":\"unknown\"")
                    .contains("\"args\":null");
        }

        @Test
        @DisplayName("args 为 List 类型")
        void listArgs() {
            ServerSentEvent<String> event = sseUtil.toolCallEvent("exec", List.of(1, 2, 3));

            assertThat(event.event()).isEqualTo("tool_call");
            assertThat(event.data()).contains("\"name\":\"exec\"").contains("[1,2,3]");
        }
    }

    // ======================== error 事件 ========================

    @Nested
    @DisplayName("errorEvent")
    class ErrorEvent {

        @Test
        @DisplayName("event 字段为 \"error\"")
        void eventTypeIsError() {
            ServerSentEvent<String> event = sseUtil.errorEvent("服务不可用");

            assertThat(event.event()).isEqualTo("error");
        }

        @Test
        @DisplayName("data 为错误描述")
        void dataIsErrorMessage() {
            ServerSentEvent<String> event = sseUtil.errorEvent("模型超时，请重试");

            assertThat(event.data()).isEqualTo("模型超时，请重试");
        }

        @Test
        @DisplayName("null → 兜底为 \"未知错误\"")
        void nullMessageFallback() {
            ServerSentEvent<String> event = sseUtil.errorEvent(null);

            assertThat(event.data()).isEqualTo("未知错误");
        }
    }

    // ======================== done 事件 ========================

    @Nested
    @DisplayName("doneEvent")
    class DoneEvent {

        @Test
        @DisplayName("event 字段为 \"done\"")
        void eventTypeIsDone() {
            ServerSentEvent<String> event = sseUtil.doneEvent();

            assertThat(event.event()).isEqualTo("done");
        }

        @Test
        @DisplayName("data 为空字符串")
        void dataIsEmpty() {
            ServerSentEvent<String> event = sseUtil.doneEvent();

            assertThat(event.data()).isEqualTo("");
        }

        @Test
        @DisplayName("多次调用返回不同实例但内容一致")
        void multipleCallsReturnConsistentEvents() {
            ServerSentEvent<String> e1 = sseUtil.doneEvent();
            ServerSentEvent<String> e2 = sseUtil.doneEvent();

            assertThat(e1).isNotSameAs(e2);
            assertThat(e1.event()).isEqualTo(e2.event());
            assertThat(e1.data()).isEqualTo(e2.data());
        }
    }

    // ======================== 无额外字段 ========================

    @Nested
    @DisplayName("SSE 事件纯净性")
    class EventPurity {

        @Test
        @DisplayName("id 字段始终为 null（不需要重连）")
        void noIdField() {
            assertThat(sseUtil.tokenEvent("x").id()).isNull();
            assertThat(sseUtil.toolCallEvent("x", null).id()).isNull();
            assertThat(sseUtil.errorEvent("x").id()).isNull();
            assertThat(sseUtil.doneEvent().id()).isNull();
        }

        @Test
        @DisplayName("comment 字段始终为 null")
        void noCommentField() {
            assertThat(sseUtil.tokenEvent("x").comment()).isNull();
            assertThat(sseUtil.doneEvent().comment()).isNull();
        }

        @Test
        @DisplayName("retry 字段始终为 null（使用 EventSource 默认重连）")
        void noRetryField() {
            assertThat(sseUtil.tokenEvent("x").retry()).isNull();
            assertThat(sseUtil.doneEvent().retry()).isNull();
        }
    }
}
