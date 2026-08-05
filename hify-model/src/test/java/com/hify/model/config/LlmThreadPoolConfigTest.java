package com.hify.model.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link LlmThreadPoolConfig} 的线程池参数与行为，
 * 按 {@code docs/specs/design/llm-invoke-spec.md §2 线程管理} 规则运行。
 * <p>
 * 直接实例化 LlmThreadPoolConfig 获取 executor，不依赖 Spring 容器，
 * 确保测试在库模块中可运行（无需 {@code @SpringBootTest}）。
 */
@DisplayName("LLM 线程池配置验证（与 LlmThreadPoolConfig 同步）")
class LlmThreadPoolConfigTest {

    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new LlmThreadPoolConfig().llmExecutor();
    }

    // ======================== 参数验证 ========================

    @Nested
    @DisplayName("线程池参数必须与规范一致")
    class ParameterValidation {

        @Test
        @DisplayName("corePoolSize = 10 — 日常并发度基准")
        void shouldHaveCorePoolSizeTen() {
            assertThat(executor.getCorePoolSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("maxPoolSize = 50 — 峰值上限")
        void shouldHaveMaxPoolSizeFifty() {
            assertThat(executor.getMaxPoolSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("queueCapacity = 200 — 缓冲 4 秒突发流量")
        void shouldHaveQueueCapacityTwoHundred() {
            // queueCapacity 无直接 getter，通过 ThreadPoolExecutor 内部获取
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
            assertThat(pool.getQueue().remainingCapacity() + pool.getQueue().size())
                    .isEqualTo(200);
        }

        @Test
        @DisplayName("keepAliveSeconds = 120 — 空闲线程 2 分钟后回收")
        void shouldHaveKeepAliveSeconds() {
            assertThat(executor.getKeepAliveSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("拒绝策略 = CallerRunsPolicy — 队列满时调用线程自己执行，不抛异常")
        void shouldUseCallerRunsPolicy() {
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
            assertThat(pool.getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        }

        @Test
        @DisplayName("线程名前缀 = llm-")
        void shouldHaveLlmThreadPrefix() {
            assertThat(executor.getThreadNamePrefix()).isEqualTo("llm-");
        }

        @Test
        @DisplayName("优雅关闭：提交的任务在 shutdown 前能执行完成")
        void shouldCompleteTasksBeforeShutdown() throws Exception {
            // 验证 waitForTasksToCompleteOnShutdown=true + awaitTerminationSeconds=30
            // 的效果：shutdown 后会等待队列中的任务执行完
            CompletableFuture<Boolean> future = new CompletableFuture<>();

            executor.execute(() -> future.complete(true));

            executor.shutdown(); // 触发优雅关闭
            assertThat(future.get(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    // ======================== 行为验证 ========================

    @Nested
    @DisplayName("线程池运行时行为")
    class RuntimeBehavior {

        @Test
        @DisplayName("任务在 llm- 前缀的线程中执行")
        void shouldExecuteInLlmNamedThread() throws Exception {
            CompletableFuture<String> future = new CompletableFuture<>();

            executor.execute(() -> {
                future.complete(Thread.currentThread().getName());
            });

            String threadName = future.get(5, TimeUnit.SECONDS);
            assertThat(threadName).startsWith("llm-");
        }

        @Test
        @DisplayName("并发提交 5 个任务 → 全部在 llm- 线程中执行")
        void shouldExecuteConcurrentTasksInLlmThreads() throws Exception {
            int taskCount = 5;
            CountDownLatch latch = new CountDownLatch(taskCount);
            AtomicInteger executedInLlmThreads = new AtomicInteger(0);

            for (int i = 0; i < taskCount; i++) {
                executor.execute(() -> {
                    if (Thread.currentThread().getName().startsWith("llm-")) {
                        executedInLlmThreads.incrementAndGet();
                    }
                    latch.countDown();
                });
            }

            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(executedInLlmThreads.get()).isEqualTo(taskCount);
        }

        @Test
        @DisplayName("Core 线程预分配后可达 10 个不同 llm- 线程")
        void shouldHaveUpToCoreThreads() throws Exception {
            // force the pool to create all core threads
            executor.setCorePoolSize(10);

            int taskCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(taskCount);
            AtomicInteger distinctThreads = new AtomicInteger(0);

            // use a shared set to count distinct thread names
            java.util.Set<String> threadNames = java.util.concurrent.ConcurrentHashMap.newKeySet();

            for (int i = 0; i < taskCount; i++) {
                executor.execute(() -> {
                    try {
                        startLatch.await(); // hold all tasks until released
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    threadNames.add(Thread.currentThread().getName());
                    doneLatch.countDown();
                });
            }

            startLatch.countDown(); // release all
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();

            // With core=10 and 10 concurrent tasks, all should get distinct threads
            assertThat(threadNames).hasSize(10);
            threadNames.forEach(name -> assertThat(name).startsWith("llm-"));
        }

        @Test
        @DisplayName("CallerRunsPolicy：队列满后任务在调用线程中直接执行")
        void shouldRunInCallerThreadWhenQueueFull() throws Exception {
            // Use a small pool to easily trigger CallerRunsPolicy
            ThreadPoolTaskExecutor smallExecutor = new ThreadPoolTaskExecutor();
            smallExecutor.setCorePoolSize(1);
            smallExecutor.setMaxPoolSize(1);
            smallExecutor.setQueueCapacity(1);
            smallExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
            smallExecutor.setThreadNamePrefix("llm-");
            smallExecutor.setWaitForTasksToCompleteOnShutdown(true);
            smallExecutor.initialize();

            try {
                CountDownLatch blockerLatch = new CountDownLatch(1);
                CountDownLatch allSubmittedLatch = new CountDownLatch(1);

                // task 1: occupies the single core thread
                smallExecutor.execute(() -> {
                    try {
                        allSubmittedLatch.countDown(); // signal: all tasks submitted
                        blockerLatch.await(); // hold the thread
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

                // task 2: fills the queue (capacity=1)
                smallExecutor.execute(() -> {});

                // wait until first task is running
                assertThat(allSubmittedLatch.await(5, TimeUnit.SECONDS)).isTrue();

                // task 3: queue full → CallerRunsPolicy → runs in caller (main) thread
                String callerThreadName = Thread.currentThread().getName();
                CompletableFuture<String> executedInThread = new CompletableFuture<>();

                // submit from main thread — should execute immediately in main thread
                smallExecutor.execute(() -> {
                    executedInThread.complete(Thread.currentThread().getName());
                });

                // callerRuns: the task was executed synchronously in the main thread
                String actualThread = executedInThread.get(1, TimeUnit.SECONDS);
                assertThat(actualThread).isEqualTo(callerThreadName); // ran in main, not llm-*

                // cleanup
                blockerLatch.countDown();
            } finally {
                smallExecutor.shutdown();
            }
        }
    }

    // ======================== Bean 声明验证 ========================

    @Nested
    @DisplayName("executor bean 声明")
    class BeanDeclaration {

        @Test
        @DisplayName("多次调用 llmExecutor() 返回不同实例（Spring @Bean 单例由容器保证）")
        void shouldCreateFreshInstanceEachCall() {
            ThreadPoolTaskExecutor e1 = new LlmThreadPoolConfig().llmExecutor();
            ThreadPoolTaskExecutor e2 = new LlmThreadPoolConfig().llmExecutor();

            assertThat(e1).isNotNull();
            assertThat(e2).isNotNull();
            assertThat(e1).isNotSameAs(e2);
        }

        @Test
        @DisplayName("executor 初始化后可正常提交任务")
        void shouldAcceptTaskAfterInitialization() throws Exception {
            CompletableFuture<Boolean> future = new CompletableFuture<>();

            executor.execute(() -> future.complete(true));

            assertThat(future.get(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
