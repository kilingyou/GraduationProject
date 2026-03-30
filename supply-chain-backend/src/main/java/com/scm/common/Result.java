package com.scm.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Generic API response wrapper.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Result<T> {

    public static final int SUCCESS = 200;
    public static final int FAIL = 500;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    /** Used for request / validation errors (e.g. {@code @Valid}). */
    public static final int BAD_REQUEST = 400;

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok() {
        return new Result<T>().setCode(SUCCESS).setMessage("success").setData(null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<T>().setCode(SUCCESS).setMessage("success").setData(data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<T>().setCode(SUCCESS).setMessage(message).setData(data);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<T>().setCode(FAIL).setMessage(message).setData(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<T>().setCode(code).setMessage(message).setData(null);
    }
}
