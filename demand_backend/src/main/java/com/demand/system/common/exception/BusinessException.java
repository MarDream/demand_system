package com.demand.system.common.exception;

public class BusinessException extends RuntimeException {

    private final int errorCode;
    private final String message;
    private final Object data;

    public BusinessException(String message) {
        super(message);
        this.errorCode = 500;
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
