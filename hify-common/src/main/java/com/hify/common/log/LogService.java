package com.hify.common.log;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 调用日志写入服务——异步非阻塞，不影响业务主流程。
 *
 * <p>所有业务模块通过注入此 Service 写日志，禁止直接调用 {@link LogRecordMapper}。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * &#64;Autowired
 * private LogService logService;
 *
 * LogRecord record = LogRecord.builder()
 *     .userId(userId).agentId(agentId).sessionId(sessionId).modelId(modelId)
 *     .traceId(traceId)
 *     .userInput(input).outputSummary(summary).toolCallsSummary(tools)
 *     .tokenUsage(tokens).durationMs(duration).status(1)
 *     .build();
 * logService.record(record);  // 异步写入，立即返回
 * }</pre>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LogService {

    private final LogRecordMapper logRecordMapper;

    /**
     * 单条异步写入——立即返回，不阻塞调用方线程。
     *
     * @param record 调用日志记录
     */
    @Async
    public void record(LogRecord record) {
        try {
            logRecordMapper.insert(record);
            log.debug("日志写入成功: traceId={}, durationMs={}", record.getTraceId(), record.getDurationMs());
        } catch (Exception e) {
            // 日志写入失败不应影响业务流程，仅记录错误
            log.error("日志写入失败: traceId={}, error={}", record.getTraceId(), e.getMessage());
        }
    }

    /**
     * 批量异步写入。
     *
     * @param records 调用日志记录列表
     */
    @Async
    public void recordBatch(List<LogRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        try {
            for (LogRecord record : records) {
                logRecordMapper.insert(record);
            }
            log.debug("批量日志写入成功: count={}", records.size());
        } catch (Exception e) {
            log.error("批量日志写入失败: count={}, error={}", records.size(), e.getMessage());
        }
    }
}
