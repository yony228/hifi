package com.hify.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link CacheConstants} 的 Key 格式和工厂方法。
 */
@DisplayName("CacheConstants — 缓存 Key 约定")
class CacheConstantsTest {

    @Nested
    @DisplayName("前缀格式")
    class PrefixFormat {

        @Test
        @DisplayName("所有前缀以 \"hify:\" 开头（复用 Constants.CACHE_PREFIX）")
        void allPrefixesStartWithHify() {
            assertThat(CacheConstants.SESSION_PREFIX).startsWith(Constants.CACHE_PREFIX);
            assertThat(CacheConstants.AGENT_CONFIG_PREFIX).startsWith(Constants.CACHE_PREFIX);
            assertThat(CacheConstants.CHAT_HISTORY_PREFIX).startsWith(Constants.CACHE_PREFIX);
            assertThat(CacheConstants.SSE_STATE_PREFIX).startsWith(Constants.CACHE_PREFIX);
            assertThat(CacheConstants.MODEL_LIST_PREFIX).startsWith(Constants.CACHE_PREFIX);
        }

        @Test
        @DisplayName("所有前缀以 \":\" 结尾")
        void allPrefixesEndWithColon() {
            assertThat(CacheConstants.SESSION_PREFIX).endsWith(":");
            assertThat(CacheConstants.AGENT_CONFIG_PREFIX).endsWith(":");
            assertThat(CacheConstants.CHAT_HISTORY_PREFIX).endsWith(":");
            assertThat(CacheConstants.SSE_STATE_PREFIX).endsWith(":");
            assertThat(CacheConstants.MODEL_LIST_PREFIX).endsWith(":");
        }

        @Test
        @DisplayName("五个前缀互不相同")
        void prefixesAreDistinct() {
            assertThat(CacheConstants.SESSION_PREFIX)
                    .isNotEqualTo(CacheConstants.AGENT_CONFIG_PREFIX)
                    .isNotEqualTo(CacheConstants.CHAT_HISTORY_PREFIX)
                    .isNotEqualTo(CacheConstants.SSE_STATE_PREFIX)
                    .isNotEqualTo(CacheConstants.MODEL_LIST_PREFIX);
        }

        @Test
        @DisplayName("前缀具体值")
        void prefixValues() {
            assertThat(CacheConstants.SESSION_PREFIX).isEqualTo("hify:session:");
            assertThat(CacheConstants.AGENT_CONFIG_PREFIX).isEqualTo("hify:agent:");
            assertThat(CacheConstants.CHAT_HISTORY_PREFIX).isEqualTo("hify:chat:");
            assertThat(CacheConstants.SSE_STATE_PREFIX).isEqualTo("hify:sse-state:");
            assertThat(CacheConstants.MODEL_LIST_PREFIX).isEqualTo("hify:model:");
        }
    }

    @Nested
    @DisplayName("Key 工厂方法")
    class KeyFactory {

        @Test
        @DisplayName("sessionKey(42L) → \"hify:session:42\"")
        void sessionKey() {
            assertThat(CacheConstants.sessionKey(42L)).isEqualTo("hify:session:42");
        }

        @Test
        @DisplayName("agentKey(7L) → \"hify:agent:7\"")
        void agentKey() {
            assertThat(CacheConstants.agentKey(7L)).isEqualTo("hify:agent:7");
        }

        @Test
        @DisplayName("chatHistoryKey(100L) → \"hify:chat:session:100\"")
        void chatHistoryKey() {
            assertThat(CacheConstants.chatHistoryKey(100L)).isEqualTo("hify:chat:session:100");
        }

        @Test
        @DisplayName("sseStateKey(200L) → \"hify:sse-state:session:200\"")
        void sseStateKey() {
            assertThat(CacheConstants.sseStateKey(200L)).isEqualTo("hify:sse-state:session:200");
        }

        @Test
        @DisplayName("modelListKey() → \"hify:model:all\"")
        void modelListKey() {
            assertThat(CacheConstants.modelListKey()).isEqualTo("hify:model:all");
        }

        @Test
        @DisplayName("modelKey(3L) → \"hify:model:3\"")
        void modelKey() {
            assertThat(CacheConstants.modelKey(3L)).isEqualTo("hify:model:3");
        }

        @Test
        @DisplayName("不同 ID 产生不同 key")
        void differentIdsProduceDifferentKeys() {
            assertThat(CacheConstants.sessionKey(1L))
                    .isNotEqualTo(CacheConstants.sessionKey(2L));
        }
    }

    @Nested
    @DisplayName("工具类特征")
    class UtilityClass {

        @Test
        @DisplayName("构造器为 private")
        void privateConstructor() throws Exception {
            var ctor = CacheConstants.class.getDeclaredConstructor();
            assertThat(ctor.canAccess(null)).isFalse();
        }

        @Test
        @DisplayName("所有字段均为 static final")
        void allFieldsAreStaticFinal() {
            for (var field : CacheConstants.class.getDeclaredFields()) {
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
