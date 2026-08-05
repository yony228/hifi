package com.hify.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局序列化配置。
 *
 * <p>对应规范 {@code docs/specs/design/api-spec.md §4 空值处理}：</p>
 * <ul>
 *   <li>全局 {@link JsonInclude.Include#NON_NULL} — null 字段不出现在 JSON 中</li>
 *   <li>顶层的 {@code code/message/data} 通过 {@code @JsonInclude(ALWAYS)} 覆盖全局规则，永不省略</li>
 *   <li>禁用 {@code WRITE_DATES_AS_TIMESTAMPS} — 日期以字符串而非数组/时间戳输出</li>
 *   <li>日期格式统一为 {@code yyyy-MM-dd HH:mm:ss}</li>
 *   <li>拒绝未知属性，防止前端拼写错误被静默忽略</li>
 * </ul>
 *
 * @see com.hify.common.dto.Result
 */
@Configuration
public class JacksonConfig {

    /** 通用日期时间格式：无 T 分隔符，空格分隔，便于人眼阅读（内部工具优先可读性）。 */
    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 在 Spring Boot 自动装配的 ObjectMapper 基础上定制序列化规则。
     * <p>
     * 优先级规则：此 Bean 返回的 customizer 会在 Boot 默认配置之后执行，因此这里的设置会覆盖默认值。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // ── 空值策略：null 字段不出现在 JSON 中（Result 的 code/message/data 单独覆盖）──
            builder.serializationInclusion(JsonInclude.Include.NON_NULL);

            // ── 日期序列化：禁用时间戳数组，统一用字符串 ──
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            // ── 反序列化：拒绝未知 JSON 属性，防止前端拼写错误被静默忽略 ──
            builder.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

            // ── Java 8 时间模块：注册后 LocalDateTime / LocalDate 才能被正确处理 ──
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            javaTimeModule.addSerializer(LocalDateTime.class,
                    new LocalDateTimeSerializer(DATETIME_FORMATTER));
            javaTimeModule.addDeserializer(LocalDateTime.class,
                    new LocalDateTimeDeserializer(DATETIME_FORMATTER));
            javaTimeModule.addSerializer(LocalDate.class,
                    new LocalDateSerializer(DATE_FORMATTER));
            javaTimeModule.addDeserializer(LocalDate.class,
                    new LocalDateDeserializer(DATE_FORMATTER));
            builder.modules(javaTimeModule);
        };
    }
}
