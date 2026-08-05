package com.jolumn.vtslshortlinkapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * vtsl-shortlink-api — 短链 API 服务（端口 8085）。
 *
 * <p>承担 URL CRUD / 重定向 / Analytics / 二级缓存 / 限流 / KGS gRPC 客户端。
 *
 * <h3>组件扫描</h3>
 * <p>必须显式扫描 vtsl-common 的 filter/interceptor/exception 等包，否则
 * {@code UserContextFilter}（ScopedValue 绑定）、{@code AuthInterceptor}（@RequireAuth）
 * 与 {@code GlobalExceptionHandler}（@RestControllerAdvice）不注册：
 * <ul>
 *   <li>UserContextFilter 不注册 → {@code UserContext.currentUserId()} 恒为 null → 全部需登录接口 401</li>
 *   <li>AuthInterceptor 不注册 → {@code @RequireAuth}/{@code @PublicApi} 注解不生效</li>
 *   <li>GlobalExceptionHandler 不注册 → BizException 变 500 裸栈</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "com.jolumn.vtslshortlinkapi",
        "com.jolumn.vtslcommon.filter",
        "com.jolumn.vtslcommon.interceptor",
        "com.jolumn.vtslcommon.util",
        "com.jolumn.vtslcommon.exception",
        "com.jolumn.vtslcommon.dto",
        "com.jolumn.vtslcommon.annotation",
        "com.jolumn.vtslcommon.context"
})
public class VtslShortlinkApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(VtslShortlinkApiApplication.class, args);
    }
}
