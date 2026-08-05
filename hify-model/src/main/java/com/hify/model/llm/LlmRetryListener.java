package com.hify.model.llm;

import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LLM 调用重试事件监听器。
 *
 * <p>当 Resilience4j 创建新 Retry 实例时，自动注册事件消费者，
 * 将重试事件以 WARN 级别记录日志，最终失败以 ERROR 级别记录。</p>
 *
 * <p>注册方式：实现 {@link RegistryEventConsumer}，由 Spring Boot 自动发现并注册。</p>
 */
@Component
@Slf4j
public class LlmRetryListener implements RegistryEventConsumer<Retry> {

    @Override
    public void onEntryAddedEvent(EntryAddedEvent<Retry> entryAddedEvent) {
        Retry retry = entryAddedEvent.getAddedEntry();
        log.info("Retry 实例已注册: {}", retry.getName());
        registerRetryListeners(retry);
    }

    @Override
    public void onEntryRemovedEvent(EntryRemovedEvent<Retry> entryRemovedEvent) {
        log.debug("Retry 实例已移除: {}", entryRemovedEvent.getRemovedEntry().getName());
    }

    @Override
    public void onEntryReplacedEvent(EntryReplacedEvent<Retry> entryReplacedEvent) {
        Retry oldRetry = entryReplacedEvent.getOldEntry();
        Retry newRetry = entryReplacedEvent.getNewEntry();
        log.info("Retry 实例已替换: {} → {}", oldRetry.getName(), newRetry.getName());
        // 在新 Retry 实例上注册事件消费者（EntryAddedEvent 构造器非 public，内联注册）
        registerRetryListeners(newRetry);
    }

    private void registerRetryListeners(Retry retry) {
        retry.getEventPublisher().onRetry(retryEvent ->
                log.warn("LLM 调用重试: name={}, attempt={}, error={}",
                        retryEvent.getName(),
                        retryEvent.getNumberOfRetryAttempts(),
                        retryEvent.getLastThrowable() != null
                                ? retryEvent.getLastThrowable().getClass().getSimpleName()
                                : "unknown"));

        retry.getEventPublisher().onError(errorEvent ->
                log.error("LLM 调用最终失败: name={}, error={}",
                        errorEvent.getName(),
                        errorEvent.getLastThrowable() != null
                                ? errorEvent.getLastThrowable().getMessage()
                                : "unknown"));
    }
}
