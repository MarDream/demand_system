package com.demand.system.common.exception;

import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Object> handleBusinessException(BusinessException e) {
        log.warn("Business exception: errorCode={}, message={}", e.getErrorCode(), e.getMessage());
        if (e.getData() != null) {
            return new Result<>(e.getErrorCode(), e.getMessage(), e.getData());
        }
        return Result.fail(e.getErrorCode(), e.getMessage());
    }

    /**
     * 处理认证失败异常（用户名/密码错误），返回401状态码
     */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("Bad credentials: {}", e.getMessage());
        return Result.fail(ErrorCode.AUTH_FAILED, "用户名或密码错误");
    }

    /**
     * 处理账户被禁用异常，返回401状态码
     */
    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleDisabledException(DisabledException e) {
        log.warn("Account disabled: {}", e.getMessage());
        return Result.fail(ErrorCode.AUTH_FAILED, "账户已被禁用，请联系管理员");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return Result.fail(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("Unexpected exception", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR, e.getMessage());
    }
}
