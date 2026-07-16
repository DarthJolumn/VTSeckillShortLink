package com.jolumn.livemallcommon.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要登录 — 验证 JWT 有效（任意角色）。
 * Gateway 验 JWT 后注入 X-User-Id / X-User-Role，
 * AuthInterceptor 校验 X-User-Id 非空。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAuth {
}