package com.jolumn.livemalluser;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * livemall-user — 用户服务（Spring MVC + VT）.
 *
 * <p>承担 8 个功能点：注册 / 登录 / Token 刷新 / 退出登录 / 设备管理 / 踢设备下线 /
 * 修改用户信息 / 封禁解禁。
 *
 * <h3>VT 纪律</h3>
 * <ul>
 *   <li>{@code spring.threads.virtual.enabled=true}：HTTP 请求跑在 VT 上</li>
 *   <li>用户上下文用 {@code ScopedValue}（{@link com.jolumn.livemallcommon.util.UserContext}）</li>
 *   <li>锁用 {@code ReentrantLock}（业务模块无 synchronized）</li>
 * </ul>
 *
 * <h3>组件扫描</h3>
 * <p>user 模块注入 common 模块的 Bean（如 {@code IdempotencyService}），
 * 需要额外扫描 {@code com.jolumn.livemallcommon} 包。
 */
@SpringBootApplication(scanBasePackages = {
        "com.jolumn.livemalluser",
        "com.jolumn.livemallcommon.util",
        "com.jolumn.livemallcommon.exception",
        "com.jolumn.livemallcommon.dto"
})
@EnableDubbo
public class LivemallUserApplication {

    static void main(String[] args) {
        SpringApplication.run(LivemallUserApplication.class, args);
    }
}
