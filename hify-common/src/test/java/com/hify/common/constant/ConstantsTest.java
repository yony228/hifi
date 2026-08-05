package com.hify.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link Constants} 的常量值与项目中其他配置对齐，
 * 确保改一处全局生效。
 */
@DisplayName("Constants — 系统常量验证")
class ConstantsTest {

    @Nested
    @DisplayName("缓存 Key 前缀")
    class CachePrefix {

        @Test
        @DisplayName("CACHE_PREFIX = \"hify:\"（与 RedisUtil 注释一致）")
        void cachePrefixValue() {
            assertThat(Constants.CACHE_PREFIX).isEqualTo("hify:");
        }

        @Test
        @DisplayName("CACHE_PREFIX 非空非空白")
        void cachePrefixNonBlank() {
            assertThat(Constants.CACHE_PREFIX).isNotBlank();
        }
    }

    @Nested
    @DisplayName("分页")
    class Page {

        @Test
        @DisplayName("DEFAULT_PAGE_SIZE = 20")
        void defaultPageSize() {
            assertThat(Constants.DEFAULT_PAGE_SIZE).isEqualTo(20);
        }

        @Test
        @DisplayName("MAX_PAGE_SIZE = 100（对齐 MybatisPlusConfig maxLimit）")
        void maxPageSize() {
            assertThat(Constants.MAX_PAGE_SIZE).isEqualTo(100);
        }

        @Test
        @DisplayName("DEFAULT_PAGE_SIZE <= MAX_PAGE_SIZE")
        void defaultPageNotGreaterThanMax() {
            assertThat(Constants.DEFAULT_PAGE_SIZE).isLessThanOrEqualTo(Constants.MAX_PAGE_SIZE);
        }

        @Test
        @DisplayName("分页值均为正数")
        void pageValuesArePositive() {
            assertThat(Constants.DEFAULT_PAGE_SIZE).isPositive();
            assertThat(Constants.MAX_PAGE_SIZE).isPositive();
        }
    }

    @Nested
    @DisplayName("Agent 运行时")
    class Agent {

        @Test
        @DisplayName("MAX_AGENT_ITERATIONS = 10")
        void maxAgentIterations() {
            assertThat(Constants.MAX_AGENT_ITERATIONS).isEqualTo(10);
        }

        @Test
        @DisplayName("MAX_AGENT_ITERATIONS > 0（防止死循环）")
        void maxIterationsIsPositive() {
            assertThat(Constants.MAX_AGENT_ITERATIONS).isPositive();
        }

        @Test
        @DisplayName("MAX_AGENT_ITERATIONS <= 20（上限合理，避免 token 浪费）")
        void maxIterationsNotTooLarge() {
            assertThat(Constants.MAX_AGENT_ITERATIONS).isLessThanOrEqualTo(20);
        }
    }

    @Nested
    @DisplayName("Token 默认值")
    class Token {

        @Test
        @DisplayName("DEFAULT_CONTEXT_WINDOW = 4096")
        void defaultContextWindow() {
            assertThat(Constants.DEFAULT_CONTEXT_WINDOW).isEqualTo(4096);
        }

        @Test
        @DisplayName("DEFAULT_MAX_OUTPUT_TOKENS = 2048")
        void defaultMaxOutputTokens() {
            assertThat(Constants.DEFAULT_MAX_OUTPUT_TOKENS).isEqualTo(2048);
        }

        @Test
        @DisplayName("MAX_INPUT_TOKENS = 32000")
        void maxInputTokens() {
            assertThat(Constants.MAX_INPUT_TOKENS).isEqualTo(32000);
        }

        @Test
        @DisplayName("Token 限制值层次合理：output < context < max_input")
        void tokenHierarchyIsSane() {
            assertThat(Constants.DEFAULT_MAX_OUTPUT_TOKENS)
                    .isLessThan(Constants.DEFAULT_CONTEXT_WINDOW);
            assertThat(Constants.DEFAULT_CONTEXT_WINDOW)
                    .isLessThan(Constants.MAX_INPUT_TOKENS);
        }
    }

    @Nested
    @DisplayName("格式化")
    class Format {

        @Test
        @DisplayName("FMT_DATETIME = \"yyyy-MM-dd HH:mm:ss\"（对齐 JacksonConfig）")
        void datetimeFormat() {
            assertThat(Constants.FMT_DATETIME).isEqualTo("yyyy-MM-dd HH:mm:ss");
        }

        @Test
        @DisplayName("FMT_DATE = \"yyyy-MM-dd\"")
        void dateFormat() {
            assertThat(Constants.FMT_DATE).isEqualTo("yyyy-MM-dd");
        }

        @Test
        @DisplayName("TIMEZONE = \"Asia/Shanghai\"")
        void timezone() {
            assertThat(Constants.TIMEZONE).isEqualTo("Asia/Shanghai");
        }

        @Test
        @DisplayName("FMT_DATETIME 和 FMT_DATE 有合理的包含关系")
        void datetimeContainsDateFormat() {
            assertThat(Constants.FMT_DATETIME).contains(Constants.FMT_DATE);
        }
    }

    @Nested
    @DisplayName("工具类特征")
    class UtilityClass {

        @Test
        @DisplayName("构造器为 private（禁止实例化）")
        void privateConstructor() throws Exception {
            var ctor = Constants.class.getDeclaredConstructor();
            assertThat(ctor.canAccess(null)).isFalse();
        }

        @Test
        @DisplayName("所有字段均为 static final")
        void allFieldsAreStaticFinal() {
            for (var field : Constants.class.getDeclaredFields()) {
                int mod = field.getModifiers();
                assertThat(java.lang.reflect.Modifier.isStatic(mod))
                        .as("字段 " + field.getName() + " 应为 static")
                        .isTrue();
                assertThat(java.lang.reflect.Modifier.isFinal(mod))
                        .as("字段 " + field.getName() + " 应为 final")
                        .isTrue();
            }
        }
    }
}
