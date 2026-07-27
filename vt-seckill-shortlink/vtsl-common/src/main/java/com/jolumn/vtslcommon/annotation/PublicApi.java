package com.jolumn.vtslcommon.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 公开接口 — 无需 JWT，任何人可访问。
 * Gateway 会在 public-paths / public-get-paths 中放行，
 * 到达业务层后 AuthInterceptor 跳过鉴权。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}