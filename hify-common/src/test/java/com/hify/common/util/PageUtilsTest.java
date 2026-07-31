package com.hify.common.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.dto.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link PageUtils#toPageResult(List, Page)} 正确提取 MyBatis-Plus 分页元信息。
 */
@DisplayName("PageUtils 分页转换验证")
class PageUtilsTest {

    @Test
    @DisplayName("正常转换：total/page/size 从 MyBatis-Plus Page 正确提取")
    void shouldConvertMybatisPlusPage() {
        // given: MyBatis-Plus 分页结果（模拟 selectPage 后的状态）
        Page<String> mpPage = new Page<>(3, 20);
        mpPage.setTotal(100);
        mpPage.setRecords(List.of("a", "b", "c"));

        // when
        PageResult<String> result = PageUtils.toPageResult(mpPage.getRecords(), mpPage);

        // then
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getMessage()).isEqualTo("ok");
        assertThat(result.getData()).containsExactly("a", "b", "c");
        assertThat(result.getTotal()).isEqualTo(100L);
        assertThat(result.getPage()).isEqualTo(3);
        assertThat(result.getSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("空列表：total=0 时正常返回空数组")
    void shouldHandleEmptyList() {
        Page<Object> mpPage = new Page<>(1, 20);
        mpPage.setTotal(0);
        mpPage.setRecords(List.of());

        PageResult<String> result = PageUtils.toPageResult(List.of(), mpPage);

        assertThat(result.getData()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(0L);
        assertThat(result.getPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("items=null 时返回空列表（通过 PageResult.of 的 null-safety 兜底）")
    void shouldReturnEmptyListWhenItemsNull() {
        Page<Object> mpPage = new Page<>(1, 20);
        mpPage.setTotal(0);

        PageResult<String> result = PageUtils.toPageResult(null, mpPage);

        assertThat(result.getData()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(0L);
    }

    @Test
    @DisplayName("mpPage=null 时抛 NPE")
    void shouldThrowNpeWhenPageNull() {
        assertThatThrownBy(() -> PageUtils.toPageResult(List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("第一页：page=1")
    void shouldHandleFirstPage() {
        Page<Object> mpPage = new Page<>(1, 10);
        mpPage.setTotal(42);

        PageResult<String> result = PageUtils.toPageResult(List.of("x"), mpPage);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
    }
}
