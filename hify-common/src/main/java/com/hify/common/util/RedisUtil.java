package com.hify.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Redis 常用操作封装。
 * <p>
 * 基于 {@link RedisTemplate}{@code <String, Object>}，提供类型安全的便捷方法。
 * 所有操作以 {@code hify:} 前缀或调用方自行管理 key 命名空间。
 * <p>
 * 使用示例：
 * <pre>{@code
 * redisUtil.set("session:123", dto, 30, TimeUnit.MINUTES);
 * SessionDto dto = redisUtil.get("session:123", SessionDto.class);
 * }</pre>
 * <p>
 * <b>开关控制</b>：通过 {@code hify.redis.enabled} 属性控制，默认开启。
 */
@Component
@ConditionalOnProperty(name = "hify.redis.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    // ======================== 写入 ========================

    /** 写入（永久有效）。 */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /** 写入并指定过期时间。 */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    // ======================== 读取 ========================

    /**
     * 读取（无类型转换，调用方自行 cast）。
     * <p>
     * 推荐使用 {@link #get(String, Class)} 获得类型安全。
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /** 读取并自动转换为指定类型。 */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    // ======================== 删除 ========================

    /** 删除单个 key。 */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /** 批量删除。 */
    public Long delete(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    // ======================== 过期 ========================

    /** 设置过期时间（key 不存在时返回 false）。 */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    // ======================== 查询 ========================

    /** key 是否存在。 */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /** 获取剩余过期时间（秒），-1 表示永久，-2 表示 key 不存在。 */
    public Long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }
}
