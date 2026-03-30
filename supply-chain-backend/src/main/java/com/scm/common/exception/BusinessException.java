package com.scm.common.exception;

import com.scm.common.Result;
import lombok.Getter;

/**
 * Business rule violation; carries an application error code (often HTTP-aligned).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(Result.FAIL, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
