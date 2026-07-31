package com.hify.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * JSON 序列化/反序列化工具 — 全局唯一的 ObjectMapper 访问入口。
 * <p>
 * 所有模块须通过此工具做 JSON 操作，禁止各自 {@code new ObjectMapper()}。
 * 内部复用 Spring 容器中经过 {@code JacksonConfig} 定制的 ObjectMapper，确保：
 * <ul>
 *   <li>{@code NON_NULL} — null 字段统一省略</li>
 *   <li>日期格式统一 — LocalDateTime → {@code yyyy-MM-dd HH:mm:ss}</li>
 *   <li>反序列化安全 — 未知属性抛异常</li>
 * </ul>
 *
 * <pre>{@code
 * // 序列化
 * String json = JsonUtil.toJson(agent);
 *
 * // 反序列化
 * AgentConfig config = JsonUtil.fromJson(jsonStr, AgentConfig.class);
 * List<AgentConfig> list = JsonUtil.fromJsonList(jsonStr, AgentConfig.class);
 * }</pre>
 */
@Component
public class JsonUtil {

    private static volatile ObjectMapper mapper;

    public JsonUtil(ObjectMapper objectMapper) {
        JsonUtil.mapper = objectMapper;
    }

    // ======================== 序列化 ========================

    /**
     * 对象 → JSON 字符串（压缩格式，无缩进）。
     * <p>等价于 {@code objectMapper.writeValueAsString(obj)}。</p>
     *
     * @throws BizException(INTERNAL_ERROR) 序列化失败（通常是对象类型不支持）
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "JSON 序列化失败: " + e.getOriginalMessage());
        }
    }

    /**
     * 对象 → JSON 字符串（带缩进，便于日志/调试阅读）。
     */
    public static String toPrettyJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "JSON 序列化失败: " + e.getOriginalMessage());
        }
    }

    // ======================== 反序列化 ========================

    /**
     * JSON 字符串 → 指定类型对象。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @return 反序列化后的对象，json 为 null 时返回 null
     * @throws BizException(INTERNAL_ERROR) JSON 格式不合法或类型不匹配
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "JSON 反序列化失败: " + e.getOriginalMessage());
        }
    }

    /**
     * JSON 字符串 → 泛型对象（如 {@code List<Agent>}、{@code Map<String, Object>}）。
     * <p>简单 List 场景推荐使用 {@link #fromJsonList(String, Class)}，更简洁。</p>
     *
     * @param json     JSON 字符串
     * @param javaType 通过 {@code mapper.getTypeFactory().constructParametricType(...)} 构造
     * @return 反序列化后的对象，json 为 null 时返回 null
     */
    public static <T> T fromJson(String json, JavaType javaType) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "JSON 反序列化失败: " + e.getOriginalMessage());
        }
    }

    /**
     * JSON 数组字符串 → {@code List<T>}。
     * <p>用法：{@code List<AgentConfig> configs = JsonUtil.fromJsonList(json, AgentConfig.class);}</p>
     *
     * @param json      JSON 数组字符串，如 {@code [{...}, {...}]}
     * @param elemClass 列表元素类型
     * @return 反序列化后的列表，json 为 null 或空字符串时返回 {@link Collections#emptyList()}
     */
    public static <T> List<T> fromJsonList(String json, Class<T> elemClass) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, elemClass);
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "JSON 反序列化失败: " + e.getOriginalMessage());
        }
    }
}
