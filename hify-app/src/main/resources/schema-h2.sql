-- ============================================================================
-- Hify Database Schema (H2 — Dev Profile)
-- Version: 1.1
--
-- 用途: 本地开发快速启动，无需外部数据库
-- 限制: 不含 pgvector (knowledge_chunk)、不含 ON UPDATE CURRENT_TIMESTAMP
-- 使用: H2 Console → http://localhost:8080/h2-console → 粘贴执行
-- ============================================================================

SET MODE MySQL;

-- ============================================================================
-- 1. 用户域 (hify-common)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(64) NOT NULL,
    `password_hash` VARCHAR(256) NOT NULL,
    `display_name` VARCHAR(128) DEFAULT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_username` (`username`)
);

-- ============================================================================
-- 2. 模型域 (hify-model)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `model_provider` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(128) NOT NULL,
    `base_url` VARCHAR(512) NOT NULL,
    `api_key` VARCHAR(512) DEFAULT NULL,
    `provider_type` VARCHAR(64) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_model_provider_name` (`name`)
);

CREATE TABLE IF NOT EXISTS `model` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `provider_id` BIGINT NOT NULL,
    `model_name` VARCHAR(256) NOT NULL,
    `model_type` VARCHAR(32) NOT NULL,
    `is_default` TINYINT NOT NULL DEFAULT 0,
    `context_window` INT DEFAULT NULL,
    `max_output_tokens` INT DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_model_provider_id` (`provider_id`),
    CONSTRAINT `fk_model_provider` FOREIGN KEY (`provider_id`) REFERENCES `model_provider` (`id`) ON DELETE CASCADE
);

-- ============================================================================
-- 3. 知识域 (hify-knowledge)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `knowledge` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `name` VARCHAR(256) NOT NULL,
    `description` TEXT DEFAULT NULL,
    `chunk_size` INT NOT NULL DEFAULT 500,
    `chunk_overlap` INT NOT NULL DEFAULT 50,
    `embedding_model_id` BIGINT DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_knowledge_user_id` (`user_id`),
    INDEX `idx_knowledge_embedding_model` (`embedding_model_id`),
    CONSTRAINT `fk_knowledge_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_knowledge_embedding_model` FOREIGN KEY (`embedding_model_id`) REFERENCES `model` (`id`) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `knowledge_id` BIGINT NOT NULL,
    `file_name` VARCHAR(512) NOT NULL,
    `file_type` VARCHAR(32) NOT NULL,
    `file_size` BIGINT NOT NULL DEFAULT 0,
    `chunk_count` INT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_knowledge_doc_kid` (`knowledge_id`),
    CONSTRAINT `fk_document_knowledge` FOREIGN KEY (`knowledge_id`) REFERENCES `knowledge` (`id`) ON DELETE CASCADE
);

-- knowledge_chunk 跳过：H2 不支持 pgvector 的 VECTOR 类型

-- ============================================================================
-- 4. Agent 域 (hify-agent)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `agent` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `name` VARCHAR(256) NOT NULL,
    `description` TEXT DEFAULT NULL,
    `model_id` BIGINT NOT NULL,
    `knowledge_id` BIGINT DEFAULT NULL,
    `system_prompt` TEXT DEFAULT NULL,
    `runtime_strategy` VARCHAR(32) NOT NULL DEFAULT 'react',
    `max_iterations` INT NOT NULL DEFAULT 10,
    `temperature` DECIMAL(3,2) NOT NULL DEFAULT 0.70,
    `top_p` DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_agent_user_id` (`user_id`),
    INDEX `idx_agent_model_id` (`model_id`),
    INDEX `idx_agent_knowledge_id` (`knowledge_id`),
    CONSTRAINT `fk_agent_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_agent_model` FOREIGN KEY (`model_id`) REFERENCES `model` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_agent_knowledge` FOREIGN KEY (`knowledge_id`) REFERENCES `knowledge` (`id`) ON DELETE SET NULL
);

-- ============================================================================
-- 5. 工具域 (hify-tool)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `tool` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(256) NOT NULL,
    `tool_type` VARCHAR(32) NOT NULL,
    `config_json` TEXT DEFAULT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `agent_tool` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `agent_id` BIGINT NOT NULL,
    `tool_id` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_agent_tool` (`agent_id`, `tool_id`),
    INDEX `idx_agent_tool_tool_id` (`tool_id`),
    CONSTRAINT `fk_agent_tool_agent` FOREIGN KEY (`agent_id`) REFERENCES `agent` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_agent_tool_tool` FOREIGN KEY (`tool_id`) REFERENCES `tool` (`id`) ON DELETE CASCADE
);

-- ============================================================================
-- 6. 工作流域 (hify-workflow)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `workflow` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `name` VARCHAR(256) NOT NULL,
    `description` TEXT DEFAULT NULL,
    `config_json` TEXT DEFAULT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_workflow_user_id` (`user_id`),
    CONSTRAINT `fk_workflow_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `workflow_node` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `workflow_id` BIGINT NOT NULL,
    `node_key` VARCHAR(128) NOT NULL,
    `node_type` VARCHAR(32) NOT NULL,
    `agent_id` BIGINT DEFAULT NULL,
    `config_json` TEXT DEFAULT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_workflow_node_key` (`workflow_id`, `node_key`),
    INDEX `idx_workflow_node_agent_id` (`agent_id`),
    CONSTRAINT `fk_node_workflow` FOREIGN KEY (`workflow_id`) REFERENCES `workflow` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_node_agent` FOREIGN KEY (`agent_id`) REFERENCES `agent` (`id`) ON DELETE SET NULL
);

-- ============================================================================
-- 7. 对话域 (hify-chat)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `session` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `agent_id` BIGINT NOT NULL,
    `title` VARCHAR(256) DEFAULT NULL,
    `context_window` INT NOT NULL DEFAULT 0,
    `message_count` INT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_session_user_id` (`user_id`),
    INDEX `idx_session_agent_id` (`agent_id`),
    CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_session_agent` FOREIGN KEY (`agent_id`) REFERENCES `agent` (`id`) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS `message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `session_id` BIGINT NOT NULL,
    `role` VARCHAR(32) NOT NULL,
    `content` TEXT DEFAULT NULL,
    `tool_calls` TEXT DEFAULT NULL,
    `token_count` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_message_session_created` (`session_id`, `created_at`),
    CONSTRAINT `fk_message_session` FOREIGN KEY (`session_id`) REFERENCES `session` (`id`) ON DELETE CASCADE
);

-- ============================================================================
-- 8. 观测域 (hify-common)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `agent_id` BIGINT NOT NULL,
    `session_id` BIGINT NOT NULL,
    `model_id` BIGINT NOT NULL,
    `user_input` TEXT DEFAULT NULL,
    `model_output` TEXT DEFAULT NULL,
    `tool_calls` TEXT DEFAULT NULL,
    `token_usage` INT NOT NULL DEFAULT 0,
    `duration_ms` INT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1,
    `error_msg` TEXT DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_log_agent_created` (`agent_id`, `created_at`),
    INDEX `idx_log_user_created` (`user_id`, `created_at`),
    INDEX `idx_log_session_id` (`session_id`),
    INDEX `idx_log_model_id` (`model_id`),
    CONSTRAINT `fk_log_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_log_agent` FOREIGN KEY (`agent_id`) REFERENCES `agent` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_log_session` FOREIGN KEY (`session_id`) REFERENCES `session` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_log_model` FOREIGN KEY (`model_id`) REFERENCES `model` (`id`) ON DELETE RESTRICT
);
