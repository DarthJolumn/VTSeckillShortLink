package com.jolumn.livemallcommon.annotation;

import com.jolumn.livemallcommon.constant.RoleEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要特定角色 — 验证 JWT 且角色在允许列表中。
 * 使用示例：@RequireRole({RoleEnum.ANCHOR, RoleEnum.ADMIN})
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    RoleEnum[] value();
}