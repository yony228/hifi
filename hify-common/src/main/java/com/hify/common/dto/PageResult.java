package com.hify.common.dto;

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
     * 创建分页成功响应。
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
}
