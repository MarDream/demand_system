package com.demand.system.common.exception;

public class BusinessException extends RuntimeException {

    private final int errorCode;
    private final String message;

    public BusinessException(String message) {
        super(message);
        this.errorCode = 500;
        this.message = message;
    }

    public BusinessException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }
}
