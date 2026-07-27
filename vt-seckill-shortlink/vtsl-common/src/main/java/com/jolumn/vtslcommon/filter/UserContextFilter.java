package com.jolumn.vtslcommon.filter;

import com.jolumn.vtslcommon.context.UserContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 用户上下文过滤器
 * 解析请求头中的用户信息，绑定到 ScopedValue
 * Gateway 通过 @ComponentScan.excludeFilters 排除
 */
@Component
public class UserContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String userIdStr = httpRequest.getHeader("X-User-Id");
        Long userId = userIdStr != null ? Long.parseLong(userIdStr) : null;

        String roleStr = httpRequest.getHeader("X-User-Role");
        Integer role = roleStr != null ? Integer.parseInt(roleStr) : null;

        String deviceId = httpRequest.getHeader("X-Device-Id");

        bindAndRun(userId, role, deviceId, () -> {
            try {
                chain.doFilter(request, response);
            } catch (IOException | ServletException e) {
                throw new RuntimeException(e);
            }
        });
    }

    static void bindAndRun(Long userId, Integer role, String deviceId, Runnable action) {
        ScopedValue.where(UserContext.USER_ID, userId)
                   .where(UserContext.ROLE, role)
                   .where(UserContext.DEVICE_ID, deviceId)
                   .run(action);
    }
}
