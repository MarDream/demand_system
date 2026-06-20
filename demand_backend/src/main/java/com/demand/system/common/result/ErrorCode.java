package com.demand.system.common.result;

public final class ErrorCode {

    private ErrorCode() {
    }

    public static final int SUCCESS = 200;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int AUTH_FAILED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    public static final int BUSINESS_ERROR = 500;
    public static final int INTERNAL_ERROR = 5000;

    // 细粒度错误码
    public static final int VALIDATION_ERROR = 40001;      // 参数校验失败
    public static final int DATABASE_ERROR = 50001;        // 数据库错误
    public static final int FILE_ERROR = 50002;            // 文件操作错误
    public static final int CONCURRENT_ERROR = 50003;      // 并发冲突错误
    public static final int NULL_POINTER_ERROR = 50004;    // 空指针错误
}
