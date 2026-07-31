package com.hify.common.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.hify.common.exception.BizException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link JsonUtil} 序列化/反序列化行为与全局 JacksonConfig 一致。
 * <p>
 * 由于 hify-common 是库模块无法启动 Spring 上下文（缺少 @SpringBootConfiguration），
 * 这里手工构造与 JacksonConfig 相同配置的 ObjectMapper 初始化 JsonUtil。
 */
@DisplayName("JsonUtil 序列化/反序列化验证")
class JsonUtilTest {

    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @BeforeAll
    static void setUp() {
        // 手工构造 ObjectMapper，配置须与 JacksonConfig 完全一致
        ObjectMapper om = new ObjectMapper();
        om.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATETIME_FMT));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATETIME_FMT));
        om.registerModule(javaTimeModule);

        new JsonUtil(om);
    }

    // ======================== toJson ========================

    @Nested
    @DisplayName("序列化 toJson / toPrettyJson")
    class Serialization {

        @Test
        @DisplayName("正常对象序列化为 JSON 字符串")
        void shouldSerializeToJson() {
            var obj = new SampleDto("hello", "world");

            String json = JsonUtil.toJson(obj);

            assertThat(json).contains("\"name\":\"hello\"");
            assertThat(json).contains("\"value\":\"world\"");
        }

        @Test
        @DisplayName("null 字段被省略（NON_NULL）")
        void shouldOmitNullFields() {
            var obj = new SampleDto("hello", null);

            String json = JsonUtil.toJson(obj);

            assertThat(json).doesNotContain("\"value\"");
            assertThat(json).contains("\"name\":\"hello\"");
        }

        @Test
        @DisplayName("LocalDateTime 按配置格式序列化")
        void shouldFormatLocalDateTime() {
            var obj = new DateDto(LocalDateTime.of(2026, 7, 31, 15, 30, 0));

            String json = JsonUtil.toJson(obj);

            assertThat(json).contains("\"time\":\"2026-07-31 15:30:00\"");
        }

        @Test
        @DisplayName("null 对象返回 null")
        void shouldReturnNullForNullInput() {
            assertThat(JsonUtil.toJson(null)).isNull();
        }

        @Test
        @DisplayName("toPrettyJson 带缩进")
        void shouldPrettyPrint() {
            var obj = new SampleDto("hello", "world");

            String json = JsonUtil.toPrettyJson(obj);

            assertThat(json).contains("\n");
            assertThat(json).contains("  \"name\"");
        }
    }

    // ======================== fromJson ========================

    @Nested
    @DisplayName("单对象反序列化 fromJson")
    class SingleDeserialization {

        @Test
        @DisplayName("合法 JSON → 对象")
        void shouldDeserializeValidJson() {
            String json = "{\"name\":\"hello\",\"value\":\"world\"}";

            SampleDto obj = JsonUtil.fromJson(json, SampleDto.class);

            assertThat(obj.name()).isEqualTo("hello");
            assertThat(obj.value()).isEqualTo("world");
        }

        @Test
        @DisplayName("null 输入返回 null")
        void shouldReturnNullForNullJson() {
            assertThat(JsonUtil.fromJson(null, SampleDto.class)).isNull();
        }

        @Test
        @DisplayName("空字符串输入返回 null")
        void shouldReturnNullForBlankJson() {
            assertThat(JsonUtil.fromJson("   ", SampleDto.class)).isNull();
        }

        @Test
        @DisplayName("日期字符串按配置格式反序列化")
        void shouldDeserializeLocalDateTime() {
            String json = "{\"time\":\"2026-07-31 15:30:00\"}";

            DateDto obj = JsonUtil.fromJson(json, DateDto.class);

            assertThat(obj.time()).isEqualTo(LocalDateTime.of(2026, 7, 31, 15, 30, 0));
        }
    }

    // ======================== fromJsonList ========================

    @Nested
    @DisplayName("列表反序列化 fromJsonList")
    class ListDeserialization {

        @Test
        @DisplayName("JSON 数组 → List")
        void shouldDeserializeJsonArray() {
            String json = "[{\"name\":\"a\",\"value\":\"1\"},{\"name\":\"b\",\"value\":\"2\"}]";

            List<SampleDto> list = JsonUtil.fromJsonList(json, SampleDto.class);

            assertThat(list).hasSize(2);
            assertThat(list.get(0).name()).isEqualTo("a");
            assertThat(list.get(1).name()).isEqualTo("b");
        }

        @Test
        @DisplayName("null 输入返回空列表")
        void shouldReturnEmptyListForNull() {
            assertThat(JsonUtil.fromJsonList(null, SampleDto.class)).isEmpty();
        }

        @Test
        @DisplayName("空字符串返回空列表")
        void shouldReturnEmptyListForBlank() {
            assertThat(JsonUtil.fromJsonList("   ", SampleDto.class)).isEmpty();
        }
    }

    // ======================== 错误处理 ========================

    @Nested
    @DisplayName("错误处理")
    class ErrorHandling {

        @Test
        @DisplayName("格式错误的 JSON 抛出 BizException")
        void shouldThrowOnMalformedJson() {
            String malformed = "{not valid json";

            assertThatThrownBy(() -> JsonUtil.fromJson(malformed, SampleDto.class))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("JSON 反序列化失败");
        }

        @Test
        @DisplayName("未知属性抛出异常（FAIL_ON_UNKNOWN_PROPERTIES）")
        void shouldThrowOnUnknownProperty() {
            String json = "{\"name\":\"hello\",\"typoField\":\"oops\"}";

            assertThatThrownBy(() -> JsonUtil.fromJson(json, SampleDto.class))
                    .isInstanceOf(BizException.class);
        }
    }

    // ======================== 辅助 record ========================

    record SampleDto(String name, String value) {}

    record DateDto(LocalDateTime time) {}
}
