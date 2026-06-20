package com.demand.system.common.exception;

import com.demand.system.common.result.ErrorCode;

public class BusinessException extends RuntimeException {

    private final int errorCode;
    private final String message;
    private final Object data;

    public BusinessException(String message) {
        super(message);
        this.errorCode = ErrorCode.BUSINESS_ERROR;
        this.message = message;
        this.data = null;
    }

    public BusinessException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
        this.data = null;
    }

    public BusinessException(int errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
        this.data = data;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
