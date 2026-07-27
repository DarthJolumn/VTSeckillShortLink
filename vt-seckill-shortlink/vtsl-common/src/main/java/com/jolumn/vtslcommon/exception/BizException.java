package com.jolumn.vtslcommon.exception;

public class BizException extends BaseException {

    public BizException(int code, String message) {
        super(code, message);
    }

    public BizException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
