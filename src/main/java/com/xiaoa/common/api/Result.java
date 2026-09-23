package com.xiaoa.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 全局统一响应结构：{code, msg, data}。
 *
 * @param <T> 业务数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Result<T> {

    private static final int SUCCESS_CODE = 0;

    private final int code;
    private final String msg;
    private final T data;

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "success", data);
    }

    public static Result<Void> success() {
        return new Result<>(SUCCESS_CODE, "success", null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public T getData() {
        return data;
    }
}
