package com.hify.common.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.dto.PageResult;

import java.util.List;

/**
 * 分页工具 — MyBatis-Plus {@link Page} 与项目 {@link PageResult} 之间的转换入口。
 * <p>
 * 所有 Service 层的列表查询统一通过此类创建分页响应，避免各模块各自写
 * {@code result.setTotal(...)} / {@code result.setPage(...)} 赋值代码。
 *
 * <pre>{@code
 * // 标准用法
 * Page<Agent> mpPage = new Page<>(page, pageSize);
 * agentMapper.selectPage(mpPage, wrapper);
 * List<AgentResponse> items = mpPage.getRecords().stream()
 *         .map(AgentResponse::from).toList();
 * return PageUtils.toPageResult(items, mpPage);
 * }</pre>
 *
 * <p>无外部依赖（仅依赖 {@code PageResult} 和 MyBatis-Plus {@code Page}），所有模块可直接使用。</p>
 */
public final class PageUtils {

    private PageUtils() {
        // 工具类禁止实例化
    }

    /**
     * 将 MyBatis-Plus 分页结果转换为项目统一分页响应。
     *
     * @param items  当前页数据（已转换为 DTO 的列表，允许空列表）
     * @param mpPage MyBatis-Plus 分页查询结果（含 total / current / size 元信息）
     * @param <T>    列表元素类型
     * @return 统一分页响应，{@code code=0, message="ok"}
     * @throws NullPointerException 如果 items 或 mpPage 为 null
     */
    public static <T> PageResult<T> toPageResult(List<T> items, Page<?> mpPage) {
        return PageResult.from(items, mpPage);
    }
}
