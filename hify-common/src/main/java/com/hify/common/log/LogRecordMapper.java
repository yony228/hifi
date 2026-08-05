package com.hify.common.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调用日志 Mapper——基于 MyBatis-Plus，继承 {@link BaseMapper} 获得通用 CRUD。
 *
 * <p>列表查询需配合时间范围筛选，禁止全表扫描。
 * 利用 DDL 中 {@code idx_log_agent_created} / {@code idx_log_user_created} 索引。</p>
 */
@Mapper
public interface LogRecordMapper extends BaseMapper<LogRecord> {
}
