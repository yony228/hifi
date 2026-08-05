package com.hify.common.log;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 调用日志实体——轻量审计索引，非全量副本。
 *
 * <p>对应 {@code docs/specs/design/data-model.md} log 表。</p>
 * <p>完整输入/输出/工具参数通过 {@code session_id} → message 表回溯，
 * log 表只存摘要，单行约 500B。</p>
 *
 * <h3>字段分类</h3>
 * <table>
 *   <tr><th>类别</th><th>字段</th></tr>
 *   <tr><td>关联</td><td>userId, agentId, sessionId, modelId, traceId</td></tr>
 *   <tr><td>摘要</td><td>userInput, outputSummary, toolCallsSummary</td></tr>
 *   <tr><td>指标</td><td>tokenUsage, durationMs, status, errorMsg</td></tr>
 *   <tr><td>时间</td><td>createdAt（自动填充）</td></tr>
 * </table>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("log")
public class LogRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    // ── 关联 ──

    /** 用户 ID（FK → user.id）。 */
    private Long userId;

    /** Agent ID（FK → agent.id）。 */
    private Long agentId;

    /** 会话 ID（FK → session.id）。 */
    private Long sessionId;

    /** 模型 ID（FK → model.id）。 */
    private Long modelId;

    /** 请求链路追踪 ID（关联 TraceFilter）。 */
    private String traceId;

    // ── 摘要（完整内容见 message 表） ──

    /** 用户输入摘要（前 500 字符）。 */
    private String userInput;

    /** 输出摘要（前 500 字符）。 */
    private String outputSummary;

    /** 工具名列表 JSON，如 {@code ["search","code_exec"]}。 */
    private String toolCallsSummary;

    // ── 指标 ──

    /** 总 token 消耗。 */
    private Integer tokenUsage;

    /** 耗时（毫秒）。 */
    private Integer durationMs;

    /** 0-失败 1-成功。 */
    private Integer status;

    /** 错误信息（失败时记录）。 */
    private String errorMsg;

    // ── 时间 ──

    /** 创建时间（数据库 DEFAULT CURRENT_TIMESTAMP）。 */
    private LocalDateTime createdAt;
}
