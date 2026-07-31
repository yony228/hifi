package com.hify.common.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 分页响应体，继承 {@link Result}，额外携带分页元信息。
 * <p>
 * JSON 结构：
 * <pre>{@code
 * {
 *   "code": 0,
 *   "message": "ok",
 *   "data": [ ... ],
 *   "total": 100,
 *   "page": 1,
 *   "size": 20
 * }
 * }</pre>
 * <p>
 * 使用静态工厂方法创建：
 * <pre>{@code
 * PageResult.of(items, total, page, size);
 * }</pre>
 *
 * @param <T> 列表元素类型
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PageResult<T> extends Result<List<T>> {

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private int page;

    /** 每页条数 */
    private int size;

    /**
     * 创建分页成功响应（手动指定所有参数）。
     *
     * @param items 当前页数据
     * @param total 总记录数
     * @param page  当前页码（1-based）
     * @param size  每页条数
     * @param <T>   列表元素类型
     */
    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        PageResult<T> result = new PageResult<>();
        result.setCode(0);
        result.setMessage("ok");
        result.setData(items != null ? items : Collections.emptyList());
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        return result;
    }

    /**
     * 从 MyBatis-Plus {@link Page} 对象创建分页成功响应。
     * <p>
     * 从 {@code mpPage} 中自动提取 {@code total}/{@code current}/{@code size}，
     * 传入已转换为 DTO 的 {@code items} 列表作为 {@code data}。
     *
     * <pre>{@code
     * Page<Agent> mpPage = agentMapper.selectPage(new Page<>(1, 20), wrapper);
     * List<AgentResponse> items = mpPage.getRecords().stream().map(AgentResponse::from).toList();
     * return PageResult.from(items, mpPage);
     * }</pre>
     *
     * @param items  当前页数据（已转换为 DTO 的列表）
     * @param mpPage MyBatis-Plus 分页结果
     * @param <T>    列表元素类型
     */
    public static <T> PageResult<T> from(List<T> items, Page<?> mpPage) {
        return of(items, mpPage.getTotal(), (int) mpPage.getCurrent(), (int) mpPage.getSize());
    }
}
