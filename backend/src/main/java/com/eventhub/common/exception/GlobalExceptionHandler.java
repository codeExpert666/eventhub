package com.eventhub.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.eventhub.common.api.ApiResponse;
import com.eventhub.common.api.ErrorCode;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一异常处理入口。
 * 这里显式区分请求体校验异常、方法参数约束异常、业务异常和未知异常，
 * 避免控制器层到处重复 try/catch，并确保所有失败响应都能落入统一的 ApiResponse 结构。
 *
 * <p>
 * {@link Slf4j} 会在编译期生成名为 {@code log} 的日志字段，
 * 语义等同于手写 {@code LoggerFactory.getLogger(GlobalExceptionHandler.class)}，
 * 但能减少每个需要日志的类都重复声明日志器的样板代码。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 @RequestBody 对象绑定后的字段校验异常。
     * 当请求体已经成功反序列化，但 Bean Validation 校验失败时，会进入该分支。
     *
     * @param exception Spring 在请求体字段校验失败时抛出的异常
     * @return 统一响应结构，data 中按字段名返回校验失败原因
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        // 将字段级校验错误整理成 field -> message 的结构，方便前端按字段展示提示信息。
        Map<String, String> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (first, second) -> first,
                        LinkedHashMap::new));
        ApiResponse<Map<String, String>> response = ApiResponse.failure(
                ErrorCode.VALIDATION_ERROR,
                "请求体参数校验失败",
                fieldErrors);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(response);
    }

    /**
     * 处理方法参数级别的约束校验异常。
     * 常见于 @RequestParam、@PathVariable 或方法级参数上直接声明校验注解的场景。
     *
     * @param exception 参数约束校验失败时抛出的异常
     * @return 统一响应结构，data 中按参数路径返回校验失败原因
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException exception) {
        // 这里使用 propertyPath 作为 key，便于定位是哪个方法参数或嵌套路径触发了约束失败。
        Map<String, String> fieldErrors = exception.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> violation.getMessage(),
                        (first, second) -> first,
                        LinkedHashMap::new));
        ApiResponse<Map<String, String>> response = ApiResponse.failure(
                ErrorCode.VALIDATION_ERROR,
                "请求参数约束校验失败",
                fieldErrors);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(response);
    }

    /**
     * 处理请求体无法反序列化的异常。
     * 当请求体为空、JSON 语法错误或字段类型明显不匹配时，通常会进入该分支。
     *
     * @param exception Spring 在请求体读取或反序列化失败时抛出的异常
     * @return 统一响应结构，data 中给出 body 维度的通用错误提示
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception) {
        ApiResponse<Map<String, String>> response = ApiResponse.failure(
                ErrorCode.VALIDATION_ERROR,
                "请求体格式不合法",
                Map.of("body", "请求体缺失或 JSON 格式错误"));
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(response);
    }

    /**
     * 处理业务层主动抛出的业务异常。
     * 该分支通常表示请求格式本身合法，但不满足业务规则，例如状态不允许、库存不足等。
     *
     * @param exception 业务层抛出的 BusinessException
     * @return 基于业务错误码和业务提示信息构建的统一响应结构
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ApiResponse<Void> response = ApiResponse.failure(
                exception.getErrorCode(),
                exception.getMessage(),
                null);
        return ResponseEntity.status(exception.getErrorCode().getHttpStatus()).body(response);
    }

    /**
     * 处理静态资源或路径资源不存在的异常。
     * 浏览器访问接口文档时通常会额外请求 favicon.ico；如果项目没有提供该静态资源，
     * Spring MVC 会抛出 NoResourceFoundException。这个场景属于可预期的客户端资源缺失，
     * 应返回 404，而不是落入未知异常分支并记录成系统内部错误。
     *
     * @param exception Spring MVC 在静态资源查找失败时抛出的异常
     * @return 统一的资源不存在响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException exception) {
        ApiResponse<Void> response = ApiResponse.failure(
                ErrorCode.NOT_FOUND,
                "请求的资源不存在",
                null);
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus()).body(response);
    }

    /**
     * 处理未被更具体分支捕获的未知异常。
     * 这里会记录完整堆栈以便排查，但返回给调用方的只会是统一的内部错误提示，避免泄露实现细节。
     *
     * @param exception 未知异常
     * @return 统一的系统内部错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled exception caught by global handler", exception);
        ApiResponse<Void> response = ApiResponse.failure(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                null);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus()).body(response);
    }
}
