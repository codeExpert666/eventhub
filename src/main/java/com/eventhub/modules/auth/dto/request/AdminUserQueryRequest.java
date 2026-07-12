package com.eventhub.modules.auth.dto.request;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.format.annotation.DateTimeFormat;

import com.eventhub.common.api.PageRequest;
import com.eventhub.modules.auth.enums.UserStatus;
import com.eventhub.modules.auth.mapper.param.UserQueryCriteria;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理员用户列表查询参数。
 *
 * <p>
 * 该对象承接 GET /api/v1/admin/users 的分页和筛选查询参数。分页参数使用默认值，筛选参数保持可选；
 * Controller 只负责绑定和校验，具体 SQL 条件由 {@link UserQueryCriteria} 传递给 Mapper。
 * </p>
 */
@Getter
@Setter
public class AdminUserQueryRequest {

    /**
     * 从 1 开始的页码。
     */
    @Min(value = 1, message = "页码不能小于 1")
    private int page = PageRequest.DEFAULT_PAGE;

    /**
     * 每页条数。
     */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = PageRequest.MAX_SIZE, message = "每页条数不能超过 100")
    private int size = PageRequest.DEFAULT_SIZE;

    /**
     * 用户名筛选，trim 后为空则忽略；当前按包含匹配处理。
     */
    @Size(max = 32, message = "用户名筛选长度不能超过 32")
    private String username;

    /**
     * 邮箱筛选，trim 后为空则忽略；当前转为小写后按包含匹配处理。
     */
    @Size(max = 128, message = "邮箱筛选长度不能超过 128")
    private String email;

    /**
     * 用户状态筛选。
     *
     * <p>
     * {@code @ModelAttribute} 查询参数绑定不走 Jackson，而是由 Spring MVC 类型转换器处理。
     * 如果这里直接声明为 {@link UserStatus}，{@code status=LOCKED} 会在字符串转枚举阶段失败，错误信息更偏框架类型转换；
     * 先用字符串承接原始输入，再用 {@link Pattern} 声明允许值，可以让非法状态稳定进入 Bean Validation，
     * 返回更明确的“用户状态只能是 ENABLED 或 DISABLED”。校验通过后再在 {@link #parseStatus()} 中转换为领域枚举。
     * </p>
     */
    @Pattern(regexp = "^(ENABLED|DISABLED)?$", message = "用户状态只能是 ENABLED 或 DISABLED")
    private String status;

    /**
     * 创建时间起点，格式示例：2026-05-01T00:00:00。
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdAtFrom;

    /**
     * 创建时间终点，格式示例：2026-05-18T23:59:59。
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdAtTo;

    /**
     * 更新时间起点，格式示例：2026-05-01T00:00:00。
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime updatedAtFrom;

    /**
     * 更新时间终点，格式示例：2026-05-18T23:59:59。
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime updatedAtTo;

    /**
     * 校验创建时间范围，避免 from 晚于 to 时继续查询数据库。
     *
     * <p>
     * 该方法不会被业务代码显式调用，而是由 {@link AssertTrue} 配合 Controller 入参上的 {@code @Valid}
     * 触发 Jakarta Bean Validation 时反射执行。方法返回 {@code false} 时，请求会在 Controller 边界被拦截为
     * 参数校验错误。方法名使用 {@code isXxx} 是为了符合 JavaBean 布尔 getter 规范，保证校验框架能稳定识别为
     * {@link AssertTrue} 属性校验。
     * </p>
     *
     * @return true 表示范围合法
     */
    @AssertTrue(message = "createdAtFrom 不能晚于 createdAtTo")
    public boolean isCreatedAtRangeValid() {
        return createdAtFrom == null || createdAtTo == null || !createdAtFrom.isAfter(createdAtTo);
    }

    /**
     * 校验更新时间范围，避免 from 晚于 to 时继续查询数据库。
     *
     * <p>
     * 该方法不会被业务代码显式调用，而是由 {@link AssertTrue} 配合 Controller 入参上的 {@code @Valid}
     * 触发 Jakarta Bean Validation 时反射执行。方法返回 {@code false} 时，请求会在 Controller 边界被拦截为
     * 参数校验错误。方法名使用 {@code isXxx} 是为了符合 JavaBean 布尔 getter 规范，保证校验框架能稳定识别为
     * {@link AssertTrue} 属性校验。
     * </p>
     *
     * @return true 表示范围合法
     */
    @AssertTrue(message = "updatedAtFrom 不能晚于 updatedAtTo")
    public boolean isUpdatedAtRangeValid() {
        return updatedAtFrom == null || updatedAtTo == null || !updatedAtFrom.isAfter(updatedAtTo);
    }

    /**
     * 转换为通用分页请求。
     *
     * @return 分页请求
     */
    public PageRequest toPageRequest() {
        return PageRequest.of(page, size);
    }

    /**
     * 转换为用户查询条件。
     *
     * @return Mapper 查询条件
     */
    public UserQueryCriteria toCriteria() {
        return UserQueryCriteria.of(
                username,
                email,
                parseStatus(),
                createdAtFrom,
                createdAtTo,
                updatedAtFrom,
                updatedAtTo
        );
    }

    private UserStatus parseStatus() {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return null;
        }
        return UserStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
