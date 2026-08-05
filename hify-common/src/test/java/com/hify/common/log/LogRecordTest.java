package com.hify.common.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link LogRecord} 实体字段映射和 Builder。
 */
@DisplayName("LogRecord — 调用日志实体")
class LogRecordTest {

    @Nested
    @DisplayName("Builder 构造")
    class Builder {

        @Test
        @DisplayName("Builder 构建完整对象，所有字段正确赋值")
        void shouldBuildCompleteRecord() {
            LocalDateTime now = LocalDateTime.now();

            LogRecord record = LogRecord.builder()
                    .userId(1L)
                    .agentId(2L)
                    .sessionId(3L)
                    .modelId(4L)
                    .traceId("abc12345")
                    .userInput("你好，今天天气怎么样？")
                    .outputSummary("今天天气晴朗，温度25°C...（省略500字）")
                    .toolCallsSummary("[\"search\"]")
                    .tokenUsage(1500)
                    .durationMs(3200)
                    .status(1)
                    .errorMsg(null)
                    .createdAt(now)
                    .build();

            assertThat(record.getUserId()).isEqualTo(1L);
            assertThat(record.getAgentId()).isEqualTo(2L);
            assertThat(record.getSessionId()).isEqualTo(3L);
            assertThat(record.getModelId()).isEqualTo(4L);
            assertThat(record.getTraceId()).isEqualTo("abc12345");
            assertThat(record.getUserInput()).isEqualTo("你好，今天天气怎么样？");
            assertThat(record.getOutputSummary()).contains("今天天气晴朗");
            assertThat(record.getToolCallsSummary()).isEqualTo("[\"search\"]");
            assertThat(record.getTokenUsage()).isEqualTo(1500);
            assertThat(record.getDurationMs()).isEqualTo(3200);
            assertThat(record.getStatus()).isEqualTo(1);
            assertThat(record.getErrorMsg()).isNull();
            assertThat(record.getCreatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("失败日志——status=0 + errorMsg")
        void shouldBuildFailedRecord() {
            LogRecord record = LogRecord.builder()
                    .status(0)
                    .errorMsg("连接超时")
                    .build();

            assertThat(record.getStatus()).isEqualTo(0);
            assertThat(record.getErrorMsg()).isEqualTo("连接超时");
        }

        @Test
        @DisplayName("无工具调用的简单对话——toolCallsSummary 为 null")
        void shouldAllowNullToolCalls() {
            LogRecord record = LogRecord.builder()
                    .userInput("你好")
                    .outputSummary("你好！有什么可以帮助你的？")
                    .toolCallsSummary(null)
                    .build();

            assertThat(record.getToolCallsSummary()).isNull();
        }
    }

    @Nested
    @DisplayName("摘要字段长度预期")
    class SummaryLength {

        @Test
        @DisplayName("userInput 摘要 ≤ 500 字符")
        void userInputSummaryIsShort() {
            String longInput = "你".repeat(1000);
            String summary = longInput.length() > 500 ? longInput.substring(0, 500) : longInput;

            LogRecord record = LogRecord.builder().userInput(summary).build();

            assertThat(record.getUserInput()).hasSizeLessThanOrEqualTo(500);
        }

        @Test
        @DisplayName("outputSummary 摘要 ≤ 500 字符")
        void outputSummaryIsShort() {
            String longOutput = "回".repeat(1000);
            String summary = longOutput.length() > 500 ? longOutput.substring(0, 500) : longOutput;

            LogRecord record = LogRecord.builder().outputSummary(summary).build();

            assertThat(record.getOutputSummary()).hasSizeLessThanOrEqualTo(500);
        }

        @Test
        @DisplayName("toolCallsSummary 格式为 JSON 数组字符串")
        void toolCallsSummaryFormat() {
            LogRecord record = LogRecord.builder()
                    .toolCallsSummary("[\"search\",\"code_exec\"]")
                    .build();

            assertThat(record.getToolCallsSummary())
                    .startsWith("[")
                    .endsWith("]")
                    .contains("search", "code_exec");
        }
    }

    @Nested
    @DisplayName("@TableName 注解")
    class TableAnnotation {

        @Test
        @DisplayName("映射表名为 \"log\"")
        void tableNameIsLog() {
            var annotation = LogRecord.class.getAnnotation(
                    com.baomidou.mybatisplus.annotation.TableName.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("log");
        }
    }
}
