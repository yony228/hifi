package com.hify.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 统一配置。
 * <p>
 * 包含三个核心能力：
 * <ul>
 *   <li><b>分页插件</b>：{@link PaginationInnerInterceptor}，单页上限 100，溢出抛异常</li>
 *   <li><b>自动填充</b>：内置 {@link MetaObjectHandler}，INSERT 时填 createdAt / updatedAt，UPDATE 时填 updatedAt</li>
 *   <li><b>逻辑删除</b>：全局配置 deleted 字段，Entity 加 {@code @TableLogic} 即可生效</li>
 * </ul>
 * <p>
 * Entity 使用示例：
 * <pre>{@code
 * // 自动填充
 * &#64;TableField(fill = FieldFill.INSERT)
 * private LocalDateTime createdAt;
 * &#64;TableField(fill = FieldFill.INSERT_UPDATE)
 * private LocalDateTime updatedAt;
 *
 * // 逻辑删除
 * &#64;TableLogic
 * private Integer deleted;  // 0=未删除, 1=已删除
 * }</pre>
 */
@Slf4j
@Configuration
@MapperScan("com.hify")
public class MybatisPlusConfig {

    // ======================== 分页插件 ========================

    /**
     * 分页拦截器。
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
        pagination.setMaxLimit(100L);   // 单页最大 100 条，超出抛异常
        pagination.setOverflow(false);  // 不静默截断

        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    // ======================== 逻辑删除 ========================

    /**
     * 全局逻辑删除配置。
     * <p>
     * 效果：
     * <ul>
     *   <li>{@code deleteById(id)} → 自动改写为 {@code UPDATE ... SET deleted=1 WHERE id=? AND deleted=0}</li>
     *   <li>{@code selectList(...)} → 自动追加 {@code WHERE deleted=0}</li>
     * </ul>
     * 需要逻辑删除的表，Entity 字段加 {@code @TableLogic}：
     * <pre>{@code
     * &#64;TableLogic
     * private Integer deleted;
     * }</pre>
     */
    @Bean
    public MybatisPlusPropertiesCustomizer mybatisPlusPropertiesCustomizer() {
        return properties -> {
            GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
            dbConfig.setLogicDeleteField("deleted");       // Entity 字段名
            dbConfig.setLogicDeleteValue("1");             // 已删除
            dbConfig.setLogicNotDeleteValue("0");          // 未删除
            properties.setGlobalConfig(new GlobalConfig().setDbConfig(dbConfig));
        };
    }

    // ======================== 自动填充 ========================

    /**
     * 自动填充处理器：INSERT 时填 createdAt / updatedAt，UPDATE 时填 updatedAt。
     * <p>
     * 不依赖数据库 DEFAULT CURRENT_TIMESTAMP，时间由应用层统一管理，
     * 方便多数据源场景和单元测试时间断言。
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {

            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                // strictInsertFill：字段不存在时抛异常，尽早暴露 Entity 字段缺失
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
                log.debug("自动填充 insert: createdAt={}", now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now);
                log.debug("自动填充 update: updatedAt={}", now);
            }
        };
    }
}
