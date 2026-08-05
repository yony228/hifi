package com.hify.common.util;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

/**
 * SSE 推送工具——chat 和 agent 模块统一的 {@link ServerSentEvent} 工厂。
 *
 * <p>对应规范 {@code docs/plan/plan-bizBase-1.0.md §5}：</p>
 * <ul>
 *   <li>{@link #tokenEvent(String)} — LLM 逐 token 输出</li>
 *   <li>{@link #toolCallEvent(String, Object)} — 工具调用中间状态</li>
 *   <li>{@link #errorEvent(String)} — 错误通知</li>
 *   <li>{@link #doneEvent()} — 流结束信号</li>
 * </ul>
 *
 * <h3>事件类型约定</h3>
 * <table>
 *   <tr><th>event</th><th>触发时机</th><th>data 格式</th></tr>
 *   <tr><td>{@code token}</td><td>LLM 每输出一个 token</td><td>纯文本</td></tr>
 *   <tr><td>{@code tool_call}</td><td>Agent 决定调用工具</td><td>{@code {"name":"...","args":{...}}}</td></tr>
 *   <tr><td>{@code error}</td><td>调用异常</td><td>错误描述</td></tr>
 *   <tr><td>{@code done}</td><td>流正常结束</td><td>空字符串</td></tr>
 * </table>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * &#64;Autowired
 * private SseUtil sseUtil;
 *
 * // Controller 中
 * &#64;GetMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
 * public Flux<ServerSentEvent<String>> stream() {
 *     return agentService.execute(prompt)
 *         .map(event -> switch (event.type()) {
 *             case TOKEN     -> sseUtil.tokenEvent(event.text());
 *             case TOOL_CALL -> sseUtil.toolCallEvent(event.toolName(), event.args());
 *             case ERROR     -> sseUtil.errorEvent(event.error());
 *             case DONE      -> sseUtil.doneEvent();
 *         });
 * }
 * }</pre>
 */
@Component
public class SseUtil {

    private final JsonUtil jsonUtil;

    public SseUtil(JsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    // ======================== 工厂方法 ========================

    /**
     * 逐 token 文本事件——前端追加到消息气泡末尾。
     *
     * @param text token 文本内容
     * @return event=token 的 SSE 事件
     */
    public ServerSentEvent<String> tokenEvent(String text) {
        return buildEvent("token", text);
    }

    /**
     * 工具调用事件——前端展示"正在调用 xxx…"中间状态。
     *
     * @param toolName 工具名称（如 "search", "code_exec"）
     * @param args 工具参数（Map / 对象 / JSON 字符串均可，自动序列化）
     * @return event=tool_call 的 SSE 事件
     */
    public ServerSentEvent<String> toolCallEvent(String toolName, Object args) {
        String data = jsonUtil.toJson(new ToolCallPayload(toolName, args));
        return buildEvent("tool_call", data);
    }

    /**
     * 错误事件——前端展示红色 toast 或内联错误提示。
     *
     * @param message 错误描述
     * @return event=error 的 SSE 事件
     */
    public ServerSentEvent<String> errorEvent(String message) {
        return buildEvent("error", message != null ? message : "未知错误");
    }

    /**
     * 流结束事件——前端关闭 EventSource 连接，结束 loading 状态。
     *
     * @return event=done 的 SSE 事件
     */
    public ServerSentEvent<String> doneEvent() {
        return buildEvent("done", "");
    }

    // ======================== 内部方法 ========================

    private ServerSentEvent<String> buildEvent(String eventType, String data) {
        return ServerSentEvent.<String>builder()
                .event(eventType)
                .data(data)
                .build();
    }

    // ======================== 内部类型 ========================

    /**
     * tool_call 事件的 data 载荷结构。
     */
    private record ToolCallPayload(String name, Object args) {}
}
