package com.demand.system.common.result;

public final class ErrorCode {

    private ErrorCode() {
    }

    public static final int SUCCESS = 200;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int BUSINESS_ERROR = 500;
    public static final int INTERNAL_ERROR = 5000;
}
