package com.hify.model.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * LLM 调用专用线程池配置。
 *
 * <p>对应规范 {@code docs/specs/design/llm-invoke-spec.md §2 线程管理}：</p>
 * <ul>
 *   <li>所有 LLM 调用通过 {@code @Async("llmExecutor")} 提交到本线程池，与 Tomcat worker 线程隔离</li>
 *   <li>核心线程 10（日常并发度），最大线程 50（峰值），队列 200（缓冲 4s 突发流量）</li>
 *   <li>拒绝策略 {@link ThreadPoolExecutor.CallerRunsPolicy} — 自然降级，不抛异常</li>
 *   <li>线程名前缀 {@code llm-}，便于监控识别</li>
 * </ul>
 *
 * <h3>参数推算</h3>
 * <table>
 *   <tr><th>参数</th><th>值</th><th>推算依据</th></tr>
 *   <tr><td>corePoolSize</td><td>10</td><td>日常 50 人并发度约 3-5%，10 线程处理稳态</td></tr>
 *   <tr><td>maxPoolSize</td><td>50</td><td>SSE 峰值连接数 ~10，乘以 5 倍余量</td></tr>
 *   <tr><td>queueCapacity</td><td>200</td><td>缓冲 4 秒内的突发流量（50 线程 × 1 req/s）</td></tr>
 *   <tr><td>RejectedExecutionPolicy</td><td>CallerRuns</td><td>拒绝策略 = 自然降级：队列满 → 调用线程自己执行</td></tr>
 * </table>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * &#64;Async("llmExecutor")
 * public CompletableFuture<ChatResponse> invokeAsync(Long modelId, Prompt prompt) {
 *     return CompletableFuture.completedFuture(doInvoke(modelId, prompt));
 * }
 * }</pre>
 *
 * @see org.springframework.scheduling.annotation.Async
 */
@Configuration
public class LlmThreadPoolConfig {

    /**
     * LLM 调用专用线程池。
     * <p>
     * 核心线程常驻 10 个，处理日常 LLM 调用；高并发时扩展到 50 个。
     * 队列容量 200，超出后由调用线程自行执行（CallerRunsPolicy 自然降级）。
     */
    @Bean("llmExecutor")
    public ThreadPoolTaskExecutor llmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // ── 线程池规模 ──
        executor.setCorePoolSize(10);          // 常驻线程（日常并发量）
        executor.setMaxPoolSize(50);           // 峰值线程（上限）
        executor.setQueueCapacity(200);        // 超过 50 并发后排队，最多排 200 个
        executor.setKeepAliveSeconds(120);     // 超出 core 的线程空闲 120s 回收

        // ── 拒绝策略：队列满 → 调用线程自己执行（自然降级，不抛异常）──
        RejectedExecutionHandler rejectedHandler = new ThreadPoolExecutor.CallerRunsPolicy();
        executor.setRejectedExecutionHandler(rejectedHandler);

        // ── 线程命名：llm- 前缀便于监控识别 ──
        executor.setThreadNamePrefix("llm-");

        // ── 优雅关闭：等待任务完成，最多 30 秒 ──
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }
}
