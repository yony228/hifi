package com.hify.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 自动填充处理器。
 * <p>
 * Entity 字段添加 {@code @TableField(fill = ...)} 注解后，
 * MyBatis-Plus 在 INSERT / UPDATE 前回调此处理器，自动填入时间值。
 * <p>
 * Entity 字段示例：
 * <pre>{@code
 * &#64;TableField(fill = FieldFill.INSERT)
 * private LocalDateTime createdAt;
 *
 * &#64;TableField(fill = FieldFill.INSERT_UPDATE)
 * private LocalDateTime updatedAt;
 * }</pre>
 * <p>
 * 优点：数据库时间戳由应用层统一管理，不依赖 MySQL {@code DEFAULT CURRENT_TIMESTAMP}，
 * 方便多数据源场景（pgvector 不走此逻辑），且便于单元测试时间断言。
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        // strictInsertFill：字段不存在时抛异常 → 尽早暴露 Entity 字段缺失问题
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
}
