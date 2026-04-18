package com.eventhub.common.exception;

import com.eventhub.common.error.ErrorCode;
import com.eventhub.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理入口。
 * 这里显式区分请求体校验异常、方法参数约束异常、业务异常和未知异常，
 * 避免控制器层到处重复 try/catch，并确保所有失败响应都能落入统一的 ApiResponse 结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 全局异常处理器专用日志记录器。
     * 这里只记录真正需要排查的未知异常，避免把用户输入导致的常规校验失败误打成错误日志。
     */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
                        LinkedHashMap::new
                ));
        ApiResponse<Map<String, String>> response = ApiResponse.failure(
                ErrorCode.VALIDATION_ERROR,
                "请求体参数校验失败",
                fieldErrors
        );
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
                        LinkedHashMap::new
                ));
        ApiResponse<Map<String, String>> response = ApiResponse.failure(
                ErrorCode.VALIDATION_ERROR,
                "请求参数约束校验失败",
                fieldErrors
        );
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
                Map.of("body", "请求体缺失或 JSON 格式错误")
        );
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
                null
        );
        return ResponseEntity.status(exception.getErrorCode().getHttpStatus()).body(response);
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
                null
        );
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus()).body(response);
    }
}
