package com.hify.model.llm;

import com.hify.model.entity.ProviderTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 按 Provider 类型动态解析 Resilience4j 熔断器/舱壁实例名称。
 *
 * <p>在 {@code @CircuitBreaker(name = "#{@providerNameResolver.resolve(#args[0].providerType)}")}
 * 等 SpEL 表达式中调用，生成如 {@code "llm-openai"}、{@code "llm-ollama"} 的实例名。</p>
 */
@Component("providerNameResolver")
public class ProviderNameResolver {

    /**
     * 根据 Provider 类型返回对应的 Resilience4j 实例名。
     *
     * @param providerType 模型提供商类型
     * @return 实例名，如 {@code "llm-openai"}
     */
    public String resolve(ProviderTypeEnum providerType) {
        return "llm-" + providerType.getDbValue();
    }
}
