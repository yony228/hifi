-- ============================================================================
-- Hify Database Schema (MySQL)
-- Version: 1.1
--
-- 对应规范: docs/specs/data-model.md
-- 引擎: InnoDB, 字符集: utf8mb4
-- 执行方式: docker compose up 时自动执行，或手动:
--   mysql -u root -p hify < schema.sql
-- ============================================================================

-- ============================================================================
-- 1. 用户域 (hify-common)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(64) NOT NULL,
    `password_hash` VARCHAR(256) NOT NULL,
    `display_name` VARCHAR(128) DEFAULT NULL,
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================================================
-- 2. 模型域 (hify-model)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `model_provider` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(128) NOT NULL,
    `base_url` VARCHAR(512) NOT NULL,
    `api_key` VARCHAR(512) DEFAULT NULL,
    `provider_type` VARCHAR(64) NOT NULL COMMENT 'openai / ollama / vllm / deepseek / custom',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_model_provider_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型提供商';

CREATE TABLE IF NOT EXISTS `model` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `provider_id` BIGINT NOT NULL,
    `model_name` VARCHAR(256) NOT NULL COMMENT '模型标识，如 gpt-4o / deepseek-v3',
    `model_type` VARCHAR(32) NOT NULL COMMENT 'llm / embedding',
    `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '0-否 1-是',
    `context_window` INT DEFAULT NULL COMMENT '上下文窗口大小（tokens）',
    `max_output_tokens` INT DEFAULT NULL COMMENT '最大输出 tokens',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_model_provider_id` (`provider_id`),
    CONSTRAINT `fk_model_provider` FOREIGN KEY (`provider_id`) REFERENCES `model_provider` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型';

-- ============================================================================
-- 3. 知识域 (hify-knowledge)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `knowledge` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `name` VARCHAR(256) NOT NULL COMMENT '知识库名称',
    `description` TEXT DEFAULT NULL,
    `chunk_size` INT NOT NULL DEFAULT 500 COMMENT '分段大小（tokens）',
    `chunk_overlap` INT NOT NULL DEFAULT 50 COMMENT '分段重叠大小',
    `embedding_model_id` BIGINT DEFAULT NULL COMMENT 'FK → model.id，向量化模型',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_knowledge_user_id` (`user_id`),
    INDEX `idx_knowledge_embedding_model` (`embedding_model_id`),
    CONSTRAINT `fk_knowledge_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_knowledge_embedding_model` FOREIGN KEY (`embedding_model_id`) REFERENCES `model` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库';

CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `knowledge_id` BIGINT NOT NULL,
    `file_name` VARCHAR(512) NOT NULL COMMENT '原始文件名',
    `file_type` VARCHAR(32) NOT NULL COMMENT 'pdf / markdown / txt',
    `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件字节数',
    `chunk_count` INT NOT NULL DEFAULT 0 COMMENT '分段数量',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-处理中 1-就绪 2-失败',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_knowledge_doc_kid` (`knowledge_id`),
    CONSTRAINT `fk_document_knowledge` FOREIGN KEY (`knowledge_id`) REFERENCES `knowledge` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档';

-- ============================================================================
-- 4. Agent 域 (hify-agent)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `agent` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `name` VARCHAR(256) NOT NULL COMMENT 'Agent 名称',
    `description` TEXT DEFAULT NULL,
    `model_id` BIGINT NOT NULL COMMENT 'FK → model.id',
    `knowledge_id` BIGINT DEFAULT NULL COMMENT 'FK → knowledge.id，可空',
    `system_prompt` TEXT DEFAULT NULL COMMENT '系统提示词',
    `runtime_strategy` VARCHAR(32) NOT NULL DEFAULT 'react' COMMENT 'react / function_call',
    `max_iterations` INT NOT NULL DEFAULT 10 COMMENT '最大迭代次数',
    `temperature` DECIMAL(3,2) NOT NULL DEFAULT 0.70 COMMENT '0.0-2.0',
    `top_p` DECIMAL(3,2) NOT NULL DEFAULT 1.00 COMMENT '0.0-1.0',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_agent_user_id` (`user_id`),
    INDEX `idx_agent_model_id` (`model_id`),
    INDEX `idx_agent_knowledge_id` (`knowledge_id`),
    CONSTRAINT `fk_agent_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_agent_model` FOREIGN KEY (`model_id`) REFERENCES `model` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_agent_knowledge` FOREIGN KEY (`knowledge_id`) REFERENCES `knowledge` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 定义';

-- ============================================================================
-- 5. 工具域 (hify-tool)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `tool` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(256) NOT NULL COMMENT '工具名称',
    `tool_type` VARCHAR(32) NOT NULL COMMENT 'web_search / code_exec / http / mcp',
    `config_json` JSON DEFAULT NULL COMMENT '工具配置（每种类型结构不同）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具定义';

CREATE TABLE IF NOT EXISTS `agent_tool` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `agent_id` BIGINT NOT NULL,
    `tool_id` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_agent_tool` (`agent_id`, `tool_id`),
    INDEX `idx_agent_tool_tool_id` (`tool_id`),
    CONSTRAINT `fk_agent_tool_agent` FOREIGN KEY (`agent_id`) REFERENCES `agent` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_agent_tool_tool` FOREIGN KEY (`tool_id`) REFERENCES `tool` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent-工具关联';

-- ============================================================================
-- 6. 工作流域 (hify-workflow)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `workflow` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `name` VARCHAR(256) NOT NULL COMMENT 'Workflow 名称',
    `description` TEXT DEFAULT NULL,
    `config_json` JSON DEFAULT NULL COMMENT '完整编排定义',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_workflow_user_id` (`user_id`),
    CONSTRAINT `fk_workflow_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workflow 定义';

CREATE TABLE IF NOT EXISTS `workflow_node` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `workflow_id` BIGINT NOT NULL,
    `node_key` VARCHAR(128) NOT NULL COMMENT '节点标识（JSON 中引用）',
    `node_type` VARCHAR(32) NOT NULL COMMENT 'llm / condition / http / agent',
    `agent_id` BIGINT DEFAULT NULL COMMENT 'FK → agent.id（node_type=agent 时使用）',
    `config_json` JSON DEFAULT NULL COMMENT '节点配置（prompt / 参数 / 条件表达式等）',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_workflow_node_key` (`workflow_id`, `node_key`),
    INDEX `idx_workflow_node_agent_id` (`agent_id`),
    CONSTRAINT `fk_node_workflow` FOREIGN KEY (`workflow_id`) REFERENCES `workflow` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_node_agent` FOREIGN KEY (`agent_id`) REFERENCES `agent` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workflow 节点';

-- ============================================================================
-- 7. 对话域 (hify-chat)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `session` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `agent_id` BIGINT NOT NULL,
    `title` VARCHAR(256) DEFAULT NULL COMMENT '会话标题（自动生成或手动）',
    `context_window` INT NOT NULL DEFAULT 0 COMMENT '当前上下文窗口大小',
    `message_count` INT NOT NULL DEFAULT 0 COMMENT '消息计数（冗余，用于列表展示）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-已结束 1-进行中',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_session_user_id` (`user_id`),
    INDEX `idx_session_agent_id` (`agent_id`),
    CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_session_agent` FOREIGN KEY (`agent_id`) REFERENCES `agent` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话';

CREATE TABLE IF NOT EXISTS `message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `session_id` BIGINT NOT NULL,
    `role` VARCHAR(32) NOT NULL COMMENT 'user / assistant / tool',
    `content` TEXT DEFAULT NULL COMMENT '消息内容',
    `tool_calls` JSON DEFAULT NULL COMMENT '工具调用记录（role=assistant 且调用了工具时）',
    `token_count` INT NOT NULL DEFAULT 0 COMMENT 'token 消耗',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_message_session_created` (`session_id`, `created_at`),
    CONSTRAINT `fk_message_session` FOREIGN KEY (`session_id`) REFERENCES `session` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息记录';

-- ============================================================================
-- 8. 观测域 (hify-common)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `agent_id` BIGINT NOT NULL,
    `session_id` BIGINT NOT NULL,
    `model_id` BIGINT NOT NULL,
    `trace_id` VARCHAR(32) DEFAULT NULL COMMENT '请求链路追踪 ID',
    `user_input` VARCHAR(500) DEFAULT NULL COMMENT '用户输入摘要（前500字符）',
    `output_summary` VARCHAR(500) DEFAULT NULL COMMENT '输出摘要（前500字符），完整回复见message表',
    `tool_calls_summary` VARCHAR(500) DEFAULT NULL COMMENT '工具名列表JSON，如["search"]，完整参数见message表',
    `token_usage` INT NOT NULL DEFAULT 0 COMMENT '总 token 消耗',
    `duration_ms` INT NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-失败 1-成功',
    `error_msg` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_log_agent_created` (`agent_id`, `created_at`),
    INDEX `idx_log_user_created` (`user_id`, `created_at`),
    INDEX `idx_log_session_id` (`session_id`),
    INDEX `idx_log_model_id` (`model_id`),
    CONSTRAINT `fk_log_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_log_agent` FOREIGN KEY (`agent_id`) REFERENCES `agent` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_log_session` FOREIGN KEY (`session_id`) REFERENCES `session` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_log_model` FOREIGN KEY (`model_id`) REFERENCES `model` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调用日志';

-- ============================================================================
-- 9. 向量存储 (pgvector — 独立于 MySQL，需在 PostgreSQL 中执行)
-- ============================================================================
-- 以下 SQL 需连接到 pgvector 数据库（hify_vectors）手动执行：
--
-- CREATE EXTENSION IF NOT EXISTS vector;
--
-- CREATE TABLE IF NOT EXISTS knowledge_chunk (
--     id BIGSERIAL PRIMARY KEY,
--     document_id BIGINT NOT NULL,
--     chunk_index INT NOT NULL,
--     chunk_text TEXT NOT NULL,
--     embedding VECTOR(1536),
--     CONSTRAINT fk_chunk_document FOREIGN KEY (document_id)
--         REFERENCES knowledge_document(id) ON DELETE CASCADE
-- );
--
-- CREATE INDEX IF NOT EXISTS idx_chunk_document_id
--     ON knowledge_chunk (document_id);
--
-- CREATE INDEX IF NOT EXISTS idx_chunk_embedding
--     ON knowledge_chunk USING ivfflat (embedding vector_cosine_ops)
--     WITH (lists = 100);
