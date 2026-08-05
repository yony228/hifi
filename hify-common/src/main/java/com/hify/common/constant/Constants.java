package com.hify.common.constant;

/**
 * 系统常量——全局唯一的魔术值收敛点。
 *
 * <p>对应规范 {@code docs/plan/plan-bizBase-1.0.md §6}。</p>
 * <p>所有模块引用此处的常量，禁止在各处硬编码字符串/数字，
 * 后期修改时只需改这一处。</p>
 *
 * <h3>分类</h3>
 * <ul>
 *   <li>{@code CACHE_*} — Redis 缓存 key 前缀</li>
 *   <li>{@code PAGE_*} — 分页限制</li>
 *   <li>{@code AGENT_*} — Agent 运行时参数</li>
 *   <li>{@code TOKEN_*} — Token 相关默认值</li>
 *   <li>{@code FMT_*} — 格式化常量</li>
 * </ul>
 */
public final class Constants {

    private Constants() {
        // 工具类，禁止实例化
    }

    // ======================== 缓存 Key 前缀 ========================

    /** 全局缓存 key 前缀，与 {@code RedisUtil} 保持一致。 */
    public static final String CACHE_PREFIX = "hify:";

    // ======================== 分页 ========================

    /** 默认每页条数。 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 单页最大条数（对齐 {@code MybatisPlusConfig} 的 {@code maxLimit}）。 */
    public static final int MAX_PAGE_SIZE = 100;

    // ======================== Agent 运行时 ========================

    /** ReAct / Function Call 最大迭代次数，防止死循环。 */
    public static final int MAX_AGENT_ITERATIONS = 10;

    // ======================== Token 默认值 ========================

    /** 模型未指定 context_window 时的默认上下文窗口（tokens）。 */
    public static final int DEFAULT_CONTEXT_WINDOW = 4096;

    /** 模型未指定 max_output_tokens 时的默认最大输出（tokens）。 */
    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 2048;

    /** 单次 LLM 调用允许的最大输入 tokens（安全上限，超此值强制截断）。 */
    public static final int MAX_INPUT_TOKENS = 32000;

    // ======================== 格式化 ========================

    /** 统一日期时间格式（对齐 {@code JacksonConfig}）。 */
    public static final String FMT_DATETIME = "yyyy-MM-dd HH:mm:ss";

    /** 统一日期格式。 */
    public static final String FMT_DATE = "yyyy-MM-dd";

    /** 系统默认时区。 */
    public static final String TIMEZONE = "Asia/Shanghai";
}
