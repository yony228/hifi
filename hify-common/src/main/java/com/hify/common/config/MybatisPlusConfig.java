package com.hify.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * <p>
 * 配置项：
 * <ul>
 *   <li><b>分页插件</b>：{@link PaginationInnerInterceptor}，自动拦截分页查询，单页上限 100</li>
 *   <li><b>自动填充</b>：由 {@link MyMetaObjectHandler} 负责 createdAt / updatedAt</li>
 *   <li><b>逻辑删除</b>：在 {@code application.yml} 的 {@code mybatis-plus.global-config.db-config} 中配置，
 *       Entity 字段加 {@code @TableLogic} 即可生效</li>
 *   <li><b>Mapper 扫描</b>：{@code @MapperScan("com.hify")} 覆盖全模块，各模块 Mapper 无需单独加 {@code @MapperScan}</li>
 * </ul>
 */
@Configuration
@MapperScan("com.hify")
public class MybatisPlusConfig {

    /**
     * 核心拦截器：分页。
     * <p>
     * 使用方式：Service 层构造 {@code Page<T>} 传入 Mapper 方法，拦截器自动改写 SQL 追加 LIMIT。
     * <pre>{@code
     * Page<Agent> page = new Page<>(1, 20);
     * agentMapper.selectPage(page, wrapper);
     * }</pre>
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大 100 条，超出直接报错（与 API 规范 size ≤ 100 保持一致）
        pagination.setMaxLimit(100L);
        // 溢出处理：超过 maxLimit 时抛出异常，而非静默截断
        pagination.setOverflow(false);

        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
