package com.hify.common.log;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证 {@link LogService} 的异步写入行为和异常保护。
 */
@DisplayName("LogService — 异步日志写入")
class LogServiceTest {

    private LogRecordMapper mapper;
    private LogService logService;

    @BeforeEach
    void setUp() {
        mapper = mock(LogRecordMapper.class);
        logService = new LogService(mapper);
    }

    @Nested
    @DisplayName("单条写入")
    class SingleRecord {

        @Test
        @DisplayName("record 调用 mapper.insert（异步）")
        void shouldCallInsert() {
            LogRecord record = LogRecord.builder()
                    .traceId("test-123")
                    .userInput("你好")
                    .build();

            logService.record(record);

            verify(mapper, timeout(2000)).insert(record);
        }

        @Test
        @DisplayName("mapper 抛异常 → 不向上传播，业务不中断")
        void shouldNotPropagateException() {
            doThrow(new RuntimeException("DB 挂了")).when(mapper).insert(any());

            // 不应抛异常
            logService.record(LogRecord.builder().traceId("err-1").build());

            // mapper 仍被调用了一次
            verify(mapper, timeout(2000)).insert(any());
        }
    }

    @Nested
    @DisplayName("批量写入")
    class BatchRecord {

        @Test
        @DisplayName("recordBatch 逐条调用 insert")
        void shouldInsertAll() {
            List<LogRecord> records = List.of(
                    LogRecord.builder().traceId("a").build(),
                    LogRecord.builder().traceId("b").build(),
                    LogRecord.builder().traceId("c").build()
            );

            logService.recordBatch(records);

            verify(mapper, timeout(2000).times(3)).insert(any());
        }

        @Test
        @DisplayName("null 列表 → 不抛异常，不调 mapper")
        void shouldHandleNullList() {
            logService.recordBatch(null);

            verify(mapper, never()).insert(any());
        }

        @Test
        @DisplayName("空列表 → 不抛异常，不调 mapper")
        void shouldHandleEmptyList() {
            logService.recordBatch(List.of());

            verify(mapper, never()).insert(any());
        }
    }
}
