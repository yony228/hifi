package com.hify.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.hify.common.dto.Result;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Jackson 全局配置与 {@link JacksonConfig} 一致，
 * 按 {@code docs/specs/api-spec.md §4 空值处理} 规则运行。
 * <p>
 * 由于 hify-common 是库模块无法使用 {@code @JsonTest}（缺少 {@code @SpringBootConfiguration}），
 * 这里手工复制 JacksonConfig 的装配逻辑构造 ObjectMapper 进行测试。
 */
@DisplayName("Jackson 序列化配置验证（与 JacksonConfig 同步）")
class JacksonConfigTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUp() {
        // ↓↓↓ 以下配置必须与 JacksonConfig 保持同步 ↓↓↓

        // 1. 全局 NON_NULL — null 字段不出现在 JSON 中
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // 2. 禁用时间戳序列化
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 3. 拒绝未知 JSON 属性
        objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // 4. Java 8 时间模块
        DateTimeFormatter datetimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(datetimeFmt));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(datetimeFmt));
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(dateFmt));
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFmt));
        objectMapper.registerModule(javaTimeModule);
    }

    // ======================== 全局 NON_NULL 策略 ========================

    @Nested
    @DisplayName("全局 NON_NULL：null 字段不出现在 JSON 中")
    class NonNullStrategy {

        @Test
        @DisplayName("null 字段被省略")
        void shouldOmitNullFields() throws JsonProcessingException {
            var obj = new DtoWithNullField("hello", null);

            String json = objectMapper.writeValueAsString(obj);

            assertThat(json).doesNotContain("\"optional\"");
            assertThat(json).contains("\"name\":\"hello\"");
        }

        @Test
        @DisplayName("非 null 字段正常序列化")
        void shouldIncludeNonNullFields() throws JsonProcessingException {
            var obj = new DtoWithNullField("hello", "world");

            String json = objectMapper.writeValueAsString(obj);

            assertThat(json).contains("\"name\":\"hello\"");
            assertThat(json).contains("\"optional\":\"world\"");
        }
    }

    // ======================== Result 顶层三元组 ========================

    @Nested
    @DisplayName("Result 的 code/message/data 通过 @JsonInclude(ALWAYS) 覆盖全局规则，永不省略")
    class ResultTopLevel {

        @Test
        @DisplayName("失败时 data=null 仍出现在 JSON 中")
        void shouldKeepDataWhenNullOnFailure() throws JsonProcessingException {
            Result<Void> fail = Result.fail("参数错误");

            String json = objectMapper.writeValueAsString(fail);

            assertThat(json).contains("\"code\":1");
            assertThat(json).contains("\"message\":\"参数错误\"");
            assertThat(json).contains("\"data\":null");
        }

        @Test
        @DisplayName("成功无返回体时 data=null 仍出现")
        void shouldKeepDataWhenNullOnSuccess() throws JsonProcessingException {
            Result<Void> ok = Result.ok();

            String json = objectMapper.writeValueAsString(ok);

            assertThat(json).contains("\"code\":0");
            assertThat(json).contains("\"message\":\"ok\"");
            assertThat(json).contains("\"data\":null");
        }

        @Test
        @DisplayName("成功带数据时 data 正常序列化，data 内部的 null 字段被省略")
        void shouldOmitNestedNullsInData() throws JsonProcessingException {
            record Person(String name, Integer age) {}
            Result<Person> ok = Result.ok(new Person("张三", null));

            String json = objectMapper.writeValueAsString(ok);

            assertThat(json).contains("\"code\":0");
            assertThat(json).contains("\"message\":\"ok\"");
            assertThat(json).contains("\"name\":\"张三\"");
            // age 为 null，NON_NULL 策略下不出现
            assertThat(json).doesNotContain("\"age\"");
        }
    }

    // ======================== 日期格式 ========================

    @Nested
    @DisplayName("日期序列化格式")
    class DateFormat {

        @Test
        @DisplayName("LocalDateTime → yyyy-MM-dd HH:mm:ss")
        void shouldFormatLocalDateTimeAsConfigured() throws JsonProcessingException {
            var dto = new DtoWithDates(
                    LocalDateTime.of(2026, 7, 27, 10, 30, 0), null);

            String json = objectMapper.writeValueAsString(dto);
            assertThat(json).contains("\"createTime\":\"2026-07-27 10:30:00\"");
        }

        @Test
        @DisplayName("LocalDate → yyyy-MM-dd")
        void shouldFormatLocalDateAsConfigured() throws JsonProcessingException {
            var dto = new DtoWithDates(null, LocalDate.of(2026, 7, 27));

            String json = objectMapper.writeValueAsString(dto);
            assertThat(json).contains("\"createDate\":\"2026-07-27\"");
        }

        @Test
        @DisplayName("null 日期被省略（NON_NULL）")
        void shouldOmitNullDate() throws JsonProcessingException {
            var dto = new DtoWithDates(null, null);

            String json = objectMapper.writeValueAsString(dto);

            assertThat(json).doesNotContain("createTime");
            assertThat(json).doesNotContain("createDate");
        }
    }

    // ======================== 反序列化安全 ========================

    @Nested
    @DisplayName("反序列化安全：未知属性抛出异常")
    class DeserializationSafety {

        @Test
        @DisplayName("JSON 带未知属性 → 抛出异常，防止前端拼写错误被静默忽略")
        void shouldFailOnUnknownProperties() {
            String unknownPropJson = "{\"name\":\"hello\",\"typoField\":\"should fail\"}";

            assertThatThrownBy(() ->
                    objectMapper.readValue(unknownPropJson, DtoWithNullField.class)
            ).isInstanceOf(JsonProcessingException.class);
        }

        @Test
        @DisplayName("正常 JSON 反序列化成功")
        void shouldDeserializeNormalJson() throws Exception {
            String json = "{\"name\":\"hello\",\"optional\":\"world\"}";

            DtoWithNullField obj = objectMapper.readValue(json, DtoWithNullField.class);

            assertThat(obj.name()).isEqualTo("hello");
            assertThat(obj.optional()).isEqualTo("world");
        }
    }

    // ======================== 辅助 DTO ========================

    record DtoWithNullField(String name, String optional) {}

    record DtoWithDates(LocalDateTime createTime, LocalDate createDate) {}
}
