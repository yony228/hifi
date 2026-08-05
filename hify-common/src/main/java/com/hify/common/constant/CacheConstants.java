package com.hify.common.constant;

/**
 * 缓存 Key 约定——所有 Redis key 的统一命名空间。
 *
 * <p>对应规范 {@code docs/plan/plan-bizBase-1.0.md §10}。</p>
 * <p>业务代码通过此处的常量和工厂方法拼装 key，
 * 禁止在各模块中裸写 {@code "hify:session:123"} 等字符串。
 * 后期改命名规则时只需改这一处。</p>
 *
 * <h3>命名空间</h3>
 * <table>
 *   <tr><th>前缀</th><th>用途</th><th>示例 key</th></tr>
 *   <tr><td>{@code hify:session:}</td><td>会话热数据</td><td>{@code hify:session:42}</td></tr>
 *   <tr><td>{@code hify:agent:}</td><td>Agent 配置缓存</td><td>{@code hify:agent:7}</td></tr>
 *   <tr><td>{@code hify:chat:}</td><td>对话历史缓存</td><td>{@code hify:chat:session:42}</td></tr>
 *   <tr><td>{@code hify:sse-state:}</td><td>SSE 流式中状态</td><td>{@code hify:sse-state:session:42}</td></tr>
 *   <tr><td>{@code hify:model:}</td><td>模型列表缓存</td><td>{@code hify:model:all}</td></tr>
 * </table>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * String sessionKey = CacheConstants.sessionKey(42L);    // "hify:session:42"
 * String agentKey   = CacheConstants.agentKey(7L);       // "hify:agent:7"
 * }</pre>
 *
 * @see Constants#CACHE_PREFIX
 */
public final class CacheConstants {

    private CacheConstants() {
        // 工具类，禁止实例化
    }

    // ======================== Key 前缀 ========================

    /** 会话热数据（最近 N 条消息）。 */
    public static final String SESSION_PREFIX = Constants.CACHE_PREFIX + "session:";

    /** Agent 配置缓存。 */
    public static final String AGENT_CONFIG_PREFIX = Constants.CACHE_PREFIX + "agent:";

    /** 对话历史缓存。 */
    public static final String CHAT_HISTORY_PREFIX = Constants.CACHE_PREFIX + "chat:";

    /** SSE 流式推送中间状态。 */
    public static final String SSE_STATE_PREFIX = Constants.CACHE_PREFIX + "sse-state:";

    /** 模型列表缓存。 */
    public static final String MODEL_LIST_PREFIX = Constants.CACHE_PREFIX + "model:";

    // ======================== Key 工厂方法 ========================

    /** 会话 key："hify:session:{id}" */
    public static String sessionKey(Long sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    /** Agent 配置 key："hify:agent:{id}" */
    public static String agentKey(Long agentId) {
        return AGENT_CONFIG_PREFIX + agentId;
    }

    /** 对话历史 key："hify:chat:session:{sessionId}" */
    public static String chatHistoryKey(Long sessionId) {
        return CHAT_HISTORY_PREFIX + "session:" + sessionId;
    }

    /** SSE 状态 key："hify:sse-state:session:{sessionId}" */
    public static String sseStateKey(Long sessionId) {
        return SSE_STATE_PREFIX + "session:" + sessionId;
    }

    /** 模型列表 key："hify:model:all" */
    public static String modelListKey() {
        return MODEL_LIST_PREFIX + "all";
    }

    /** 单个模型 key："hify:model:{id}" */
    public static String modelKey(Long modelId) {
        return MODEL_LIST_PREFIX + modelId;
    }
}
