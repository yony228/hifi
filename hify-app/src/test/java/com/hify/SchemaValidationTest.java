package com.hify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 schema-h2.sql 能在 H2 数据库中正确执行。
 * 执行完成后检查全部 12 张表是否创建成功。
 */
@SpringBootTest
@ActiveProfiles("dev")
class SchemaValidationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("执行 schema-h2.sql 并验证 12 张业务表全部创建成功")
    void shouldCreateAllTables() throws Exception {
        // given: H2 空库 + schema-h2.sql
        // 先清理可能存在的表（测试隔离）
        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("schema-h2.sql"));
        }

        // when: 查询所有表名
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' ORDER BY TABLE_NAME",
                String.class
        ).stream()
                .map(String::toLowerCase)
                .toList();

        // then: 12 张业务表（knowledge_chunk 跳过，因为 H2 不支持 VECTOR）
        assertThat(tables).contains(
                "user",
                "model_provider", "model",
                "knowledge", "knowledge_document",
                "agent",
                "tool", "agent_tool",
                "workflow", "workflow_node",
                "session", "message",
                "log"
        );

        // 验证 knowledge 表的默认值生效
        String chunkSize = jdbcTemplate.queryForObject(
                "SELECT COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'KNOWLEDGE' AND COLUMN_NAME = 'CHUNK_SIZE'",
                String.class
        );
        assertThat(chunkSize).isEqualTo("500");
    }
}
