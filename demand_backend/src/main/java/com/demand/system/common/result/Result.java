package com.demand.system.common.result;

import lombok.Data;

@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCode.SUCCESS, "操作成功", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(ErrorCode.SUCCESS, "操作成功", null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(ErrorCode.BUSINESS_ERROR, message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
