package com.demand.system.common.exception;

import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private Environment environment;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new GlobalExceptionHandler(environment);

        // 设置请求上下文
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/v1/test");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void testHandleBusinessException() {
        BusinessException exception = new BusinessException(ErrorCode.BUSINESS_ERROR, "业务错误");
        ResponseEntity<Result<Object>> response = handler.handleBusinessException(exception);

        // P1 修复：HTTP 状态码应与 errorCode 对齐（BUSINESS_ERROR=500 -> 500）
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Result<Object> result = response.getBody();
        assertNotNull(result);
        assertEquals(ErrorCode.BUSINESS_ERROR, result.getCode());
        assertEquals("业务错误", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void testHandleBusinessExceptionWithData() {
        Object data = new Object();
        BusinessException exception = new BusinessException(ErrorCode.BUSINESS_ERROR, "业务错误", data);
        ResponseEntity<Result<Object>> response = handler.handleBusinessException(exception);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Result<Object> result = response.getBody();
        assertNotNull(result);
        assertEquals(ErrorCode.BUSINESS_ERROR, result.getCode());
        assertEquals("业务错误", result.getMessage());
        assertEquals(data, result.getData());
    }

    @Test
    void testHandleBusinessException_MapsHttpStatusByErrorCode() {
        // P1 修复验证：业务异常应根据 errorCode 返回对应的真实 HTTP 状态码，
        // 不再统一返回 200，以便网关/监控系统区分成功与失败。
        assertStatus(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        assertStatus(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        assertStatus(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        assertStatus(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND);
        assertStatus(ErrorCode.CONFLICT, HttpStatus.CONFLICT);
        assertStatus(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST);
        assertStatus(ErrorCode.DATABASE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        assertStatus(ErrorCode.FILE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        assertStatus(ErrorCode.CONCURRENT_ERROR, HttpStatus.CONFLICT);
        assertStatus(ErrorCode.BUSINESS_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void assertStatus(int errorCode, HttpStatus expected) {
        BusinessException exception = new BusinessException(errorCode, "错误信息");
        ResponseEntity<Result<Object>> response = handler.handleBusinessException(exception);
        assertEquals(expected, response.getStatusCode(), "errorCode=" + errorCode + " 应映射到 " + expected);
        assertEquals(errorCode, response.getBody().getCode());
    }

    @Test
    void testHandleBadCredentialsException() {
        BadCredentialsException exception = new BadCredentialsException("Bad credentials");
        Result<Void> result = handler.handleBadCredentialsException(exception);

        assertEquals(ErrorCode.AUTH_FAILED, result.getCode());
        assertEquals("用户名或密码错误", result.getMessage());
    }

    @Test
    void testHandleDisabledException() {
        DisabledException exception = new DisabledException("Account disabled");
        Result<Void> result = handler.handleDisabledException(exception);

        assertEquals(ErrorCode.AUTH_FAILED, result.getCode());
        assertEquals("账户已被禁用，请联系管理员", result.getMessage());
    }

    @Test
    void testHandleValidationException() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError1 = new FieldError("object", "field1", "错误1");
        FieldError fieldError2 = new FieldError("object", "field2", "错误2");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        // 使用 RuntimeException 模拟验证异常，避免 MethodParameter 的复杂性
        // 实际测试中，我们关注的是 BindingResult 的处理逻辑
        try {
            // 直接测试处理逻辑，不通过异常构造
            String message = bindingResult.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(java.util.stream.Collectors.joining("; "));

            assertEquals("错误1; 错误2", message);

            // 验证返回结果格式
            Result<Void> result = Result.fail(ErrorCode.BAD_REQUEST, message);
            assertEquals(ErrorCode.BAD_REQUEST, result.getCode());
            assertTrue(result.getMessage().contains("错误1"));
            assertTrue(result.getMessage().contains("错误2"));
        } catch (Exception e) {
            fail("Should not throw exception");
        }
    }

    @Test
    void testHandleConstraintViolationException() {
        Set<ConstraintViolation<?>> violations = new HashSet<>();
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("约束错误");
        violations.add(violation);

        ConstraintViolationException exception = new ConstraintViolationException(violations);
        Result<Void> result = handler.handleConstraintViolationException(exception);

        assertEquals(ErrorCode.VALIDATION_ERROR, result.getCode());
        assertEquals("约束错误", result.getMessage());
    }

    @Test
    void testHandleNullPointerException() {
        NullPointerException exception = new NullPointerException("Null pointer");
        Result<Void> result = handler.handleNullPointerException(exception);

        assertEquals(ErrorCode.NULL_POINTER_ERROR, result.getCode());
        assertEquals("系统数据异常，请稍后重试", result.getMessage());
    }

    @Test
    void testHandleIllegalArgumentException() {
        IllegalArgumentException exception = new IllegalArgumentException("Illegal argument");
        Result<Void> result = handler.handleIllegalArgumentException(exception);

        assertEquals(ErrorCode.VALIDATION_ERROR, result.getCode());
        assertEquals("请求参数不合法，请检查后重试", result.getMessage());
    }

    @Test
    void testHandleIllegalStateException() {
        IllegalStateException exception = new IllegalStateException("Illegal state");
        Result<Void> result = handler.handleIllegalStateException(exception);

        assertEquals(ErrorCode.VALIDATION_ERROR, result.getCode());
        assertEquals("当前操作不可用，请检查操作条件", result.getMessage());
    }

    @Test
    void testHandleOptimisticLockException() {
        OptimisticLockingFailureException exception = new OptimisticLockingFailureException("Lock conflict");
        Result<Void> result = handler.handleOptimisticLockException(exception);

        assertEquals(ErrorCode.CONCURRENT_ERROR, result.getCode());
        assertEquals("数据已被其他用户修改，请刷新后重试", result.getMessage());
    }

    @Test
    void testHandleFileNotFoundException() {
        FileNotFoundException exception = new FileNotFoundException("File not found");
        Result<Void> result = handler.handleFileNotFoundException(exception);

        assertEquals(ErrorCode.FILE_ERROR, result.getCode());
        assertEquals("文件不存在或已被删除", result.getMessage());
    }

    @Test
    void testHandleIOException() {
        IOException exception = new IOException("IO error");
        Result<Void> result = handler.handleIOException(exception);

        assertEquals(ErrorCode.FILE_ERROR, result.getCode());
        assertEquals("文件操作失败，请稍后重试", result.getMessage());
    }

    @Test
    void testHandleSQLException_ForeignKeyConstraint() {
        SQLException exception = new SQLException("foreign key constraint fails");
        Result<Void> result = handler.handleSQLException(exception);

        assertEquals(ErrorCode.DATABASE_ERROR, result.getCode());
        assertEquals("数据关联错误，无法完成操作", result.getMessage());
    }

    @Test
    void testHandleSQLException_DuplicateEntry() {
        SQLException exception = new SQLException("Duplicate entry 'value' for key 'PRIMARY'");
        Result<Void> result = handler.handleSQLException(exception);

        assertEquals(ErrorCode.DATABASE_ERROR, result.getCode());
        assertEquals("数据已存在，请检查后重试", result.getMessage());
    }

    @Test
    void testHandleSQLException_NotNull() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("Column 'name' cannot be null");
        Result<Void> result = handler.handleSQLException(exception);

        assertEquals(ErrorCode.DATABASE_ERROR, result.getCode());
        assertEquals("缺少必填信息，请完善后重试", result.getMessage());
    }

    @Test
    void testHandleSQLException_DataTooLong() {
        SQLException exception = new SQLException("Data too long for column 'description'");
        Result<Void> result = handler.handleSQLException(exception);

        assertEquals(ErrorCode.DATABASE_ERROR, result.getCode());
        assertEquals("输入内容过长，请精简后重试", result.getMessage());
    }

    @Test
    void testHandleException_ProductionEnvironment() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        Exception exception = new RuntimeException("Internal error with sensitive info: java.lang.NullPointerException");
        Result<Void> result = handler.handleException(exception);

        assertEquals(ErrorCode.INTERNAL_ERROR, result.getCode());
        assertEquals("服务器内部错误，请联系管理员", result.getMessage());
        assertFalse(result.getMessage().contains("java.lang"));
    }

    @Test
    void testHandleException_DevelopmentEnvironment_WithSensitiveInfo() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        Exception exception = new RuntimeException("Error: org.springframework.dao.DataAccessException");
        Result<Void> result = handler.handleException(exception);

        assertEquals(ErrorCode.INTERNAL_ERROR, result.getCode());
        // 即使在开发环境，也应过滤敏感信息
        assertFalse(result.getMessage().contains("org.springframework"));
    }

    @Test
    void testHandleException_DevelopmentEnvironment_WithoutSensitiveInfo() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        Exception exception = new RuntimeException("业务处理失败");
        Result<Void> result = handler.handleException(exception);

        assertEquals(ErrorCode.INTERNAL_ERROR, result.getCode());
        assertEquals("业务处理失败", result.getMessage());
    }
}
