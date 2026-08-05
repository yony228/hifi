package com.hify.model.llm;

import com.hify.model.entity.ProviderTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProviderNameResolver — 动态熔断器名称")
class ProviderNameResolverTest {

    private ProviderNameResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ProviderNameResolver();
    }

    @Test
    @DisplayName("OPENAI → \"llm-openai\"")
    void openAi() {
        assertThat(resolver.resolve(ProviderTypeEnum.OPENAI)).isEqualTo("llm-openai");
    }

    @Test
    @DisplayName("OLLAMA → \"llm-ollama\"")
    void ollama() {
        assertThat(resolver.resolve(ProviderTypeEnum.OLLAMA)).isEqualTo("llm-ollama");
    }

    @Test
    @DisplayName("VLLM → \"llm-vllm\"")
    void vllm() {
        assertThat(resolver.resolve(ProviderTypeEnum.VLLM)).isEqualTo("llm-vllm");
    }

    @Test
    @DisplayName("DEEPSEEK → \"llm-deepseek\"")
    void deepseek() {
        assertThat(resolver.resolve(ProviderTypeEnum.DEEPSEEK)).isEqualTo("llm-deepseek");
    }

    @Test
    @DisplayName("CUSTOM → \"llm-custom\"")
    void custom() {
        assertThat(resolver.resolve(ProviderTypeEnum.CUSTOM)).isEqualTo("llm-custom");
    }
}
