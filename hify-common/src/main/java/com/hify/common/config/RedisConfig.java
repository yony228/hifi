package com.hify.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置：自定义序列化方式。
 * <p>
 * 序列化策略：
 * <ul>
 *   <li><b>Key</b>：{@link StringRedisSerializer} — 可读，便于 redis-cli 调试</li>
 *   <li><b>Value</b>：{@link GenericJackson2JsonRedisSerializer} — JSON 格式，
 *       自动写入 {@code @class} 类型信息，反序列化无需指定目标类型</li>
 *   <li><b>Hash Key/Value</b>：同上</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * redisTemplate.opsForValue().set("session:123", sessionDto);
 * SessionDto dto = (SessionDto) redisTemplate.opsForValue().get("session:123");
 * }</pre>
 * 建议通过 {@link com.hify.common.util.RedisUtil} 包装类操作，提供类型安全和过期时间便利方法。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 序列化：可读字符串
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 序列化：JSON（含 @class 类型信息）
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
