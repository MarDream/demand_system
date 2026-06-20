package com.demand.system.common.exception;

import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Environment environment;

    public GlobalExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Object> handleBusinessException(BusinessException e) {
        logExceptionWithContext("Business exception", e);
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
        logExceptionWithContext("Bad credentials", e);
        return Result.fail(ErrorCode.AUTH_FAILED, "用户名或密码错误");
    }

    /**
     * 处理账户被禁用异常，返回401状态码
     */
    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleDisabledException(DisabledException e) {
        logExceptionWithContext("Account disabled", e);
        return Result.fail(ErrorCode.AUTH_FAILED, "账户已被禁用，请联系管理员");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        logExceptionWithContext("Validation failed: " + message, e);
        return Result.fail(ErrorCode.BAD_REQUEST, message);
    }

    /**
     * 处理JSR-303约束校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        logExceptionWithContext("Constraint violation: " + message, e);
        return Result.fail(ErrorCode.VALIDATION_ERROR, message);
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleNullPointerException(NullPointerException e) {
        logExceptionWithContext("Null pointer exception", e);
        return Result.fail(ErrorCode.NULL_POINTER_ERROR, "系统数据异常，请稍后重试");
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        logExceptionWithContext("Illegal argument", e);
        return Result.fail(ErrorCode.VALIDATION_ERROR, "请求参数不合法，请检查后重试");
    }

    /**
     * 处理非法状态异常
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalStateException(IllegalStateException e) {
        logExceptionWithContext("Illegal state", e);
        return Result.fail(ErrorCode.VALIDATION_ERROR, "当前操作不可用，请检查操作条件");
    }

    /**
     * 处理乐观锁异常
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleOptimisticLockException(OptimisticLockingFailureException e) {
        logExceptionWithContext("Optimistic lock conflict", e);
        return Result.fail(ErrorCode.CONCURRENT_ERROR, "数据已被其他用户修改，请刷新后重试");
    }

    /**
     * 处理文件未找到异常
     */
    @ExceptionHandler(FileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleFileNotFoundException(FileNotFoundException e) {
        logExceptionWithContext("File not found", e);
        return Result.fail(ErrorCode.FILE_ERROR, "文件不存在或已被删除");
    }

    /**
     * 处理IO异常
     */
    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleIOException(IOException e) {
        logExceptionWithContext("IO exception", e);
        return Result.fail(ErrorCode.FILE_ERROR, "文件操作失败，请稍后重试");
    }

    /**
     * 处理SQL异常和数据完整性异常
     */
    @ExceptionHandler({SQLException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleSQLException(Exception e) {
        logExceptionWithContext("Database exception", e);
        String userMessage = resolveSQLErrorMessage(e);
        return Result.fail(ErrorCode.DATABASE_ERROR, userMessage);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        logExceptionWithContext("Unexpected exception", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR, resolveUnexpectedErrorMessage(e));
    }

    /**
     * 清理错误信息中的敏感内容
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return "服务器内部错误，请联系管理员";
        }

        // 移除技术栈信息
        String[] sensitivePatterns = {
                "java\\.", "javax\\.", "jakarta\\.",
                "org\\.springframework\\.", "org\\.apache\\.",
                "com\\.mysql\\.", "com\\.baomidou\\.",
                "at com\\.", "at org\\.", "at java\\.",
                "Caused by:", "Exception in thread",
                "\\$Proxy", "\\$\\$EnhancerBy"
        };

        String sanitized = message;
        for (String pattern : sensitivePatterns) {
            sanitized = sanitized.replaceAll(pattern + ".*", "");
        }

        // 移除SQL语句片段
        sanitized = sanitized.replaceAll("(?i)(select|insert|update|delete|from|where)\\s+.*", "");

        // 移除文件路径
        sanitized = sanitized.replaceAll("[A-Za-z]:\\\\.*", "");
        sanitized = sanitized.replaceAll("/[a-z/]+/.*", "");

        sanitized = sanitized.trim();
        if (sanitized.isEmpty()) {
            return "服务器内部错误，请联系管理员";
        }

        return sanitized;
    }

    private String resolveSQLErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "数据操作失败，请稍后重试";
        }

        // 外键约束错误
        if (message.contains("foreign key constraint")) {
            return "数据关联错误，无法完成操作";
        }

        // 唯一约束错误
        if (message.contains("Duplicate entry") || message.contains("unique constraint")) {
            return "数据已存在，请检查后重试";
        }

        // 非空约束错误
        if (message.contains("cannot be null") || message.contains("not-null")) {
            return "缺少必填信息，请完善后重试";
        }

        // 数据过长错误
        if (message.contains("Data too long")) {
            return "输入内容过长，请精简后重试";
        }

        // 表或列不存在
        if (message.contains("Table") && message.contains("doesn't exist")) {
            return "系统配置异常，请联系管理员";
        }

        if (message.contains("Unknown column")) {
            return "系统配置异常，请联系管理员";
        }

        // 版本冲突
        if (message.contains("version") || message.contains("OptimisticLock")) {
            return "数据已被其他用户修改，请刷新后重试";
        }

        // 不返回原始SQL错误信息，避免泄露表结构
        log.warn("SQL error detail (not exposed to client): {}", message);
        return "数据操作失败，请稍后重试或联系管理员";
    }

    private String resolveUnexpectedErrorMessage(Exception e) {
        boolean isProduction = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));

        if (isProduction) {
            return "服务器内部错误，请联系管理员";
        }

        // 即使在开发环境，也要清理敏感信息
        String message = e.getMessage();
        if (message == null) {
            return "服务器内部错误，请联系管理员";
        }

        // 检查是否包含敏感技术信息
        if (containsSensitiveInfo(message)) {
            log.warn("Sanitizing error message containing sensitive info");
            return sanitizeErrorMessage(message);
        }

        return message;
    }

    /**
     * 检查消息是否包含敏感信息
     */
    private boolean containsSensitiveInfo(String message) {
        String[] sensitiveKeywords = {
                "java.", "javax.", "jakarta.",
                "org.springframework", "org.apache",
                "com.mysql", "com.baomidou",
                "SQLException", "Exception in thread",
                "Caused by:", "$Proxy",
                "SELECT", "INSERT", "UPDATE", "DELETE", "FROM", "WHERE"
        };

        String upperMessage = message.toUpperCase();
        for (String keyword : sensitiveKeywords) {
            if (upperMessage.contains(keyword.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 记录异常日志并附加请求上下文信息
     */
    private void logExceptionWithContext(String message, Exception e) {
        // 生成或获取追踪ID
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
            MDC.put("traceId", traceId);
        }

        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String clientIp = getClientIp(request);

            // 根据异常类型选择日志级别
            if (e instanceof BusinessException ||
                e instanceof IllegalArgumentException ||
                e instanceof IllegalStateException ||
                e instanceof ConstraintViolationException) {
                log.warn("{} - traceId={}, method={}, uri={}, ip={}, error={}",
                        message, traceId, method, uri, clientIp, e.getMessage());
            } else {
                log.error("{} - traceId={}, method={}, uri={}, ip={}",
                        message, traceId, method, uri, clientIp, e);
            }
        } else {
            // 非HTTP请求上下文（如异步任务）
            if (e instanceof BusinessException ||
                e instanceof IllegalArgumentException ||
                e instanceof IllegalStateException) {
                log.warn("{} - traceId={}, error={}", message, traceId, e.getMessage());
            } else {
                log.error("{} - traceId={}", message, traceId, e);
            }
        }
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 取第一个IP（可能有多级代理）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
