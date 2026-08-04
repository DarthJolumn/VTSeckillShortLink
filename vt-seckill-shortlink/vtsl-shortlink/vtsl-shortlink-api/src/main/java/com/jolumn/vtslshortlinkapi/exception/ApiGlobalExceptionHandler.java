package com.jolumn.vtslshortlinkapi.exception;

import com.jolumn.vtslcommon.dto.Result;
import com.jolumn.vtslcommon.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiGlobalExceptionHandler.class);

    private static final Map<String, String> GO_FIELD_MAP = Map.ofEntries(
            Map.entry("originalUrl", "OriginalURL"),
            Map.entry("shortKey", "ShortKey"),
            Map.entry("shortUrl", "ShortKey"),
            Map.entry("username", "Username"),
            Map.entry("email", "Email"),
            Map.entry("password", "Password"),
            Map.entry("title", "Title")
    );

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            String goField = GO_FIELD_MAP.getOrDefault(fe.getField(), fe.getField());
            errors.put(goField, "Invalid " + goField);
        }
        return Result.error(400, "Validation failed");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.error(400, "Invalid request format");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return Result.error(500, "Internal server error");
    }
}
