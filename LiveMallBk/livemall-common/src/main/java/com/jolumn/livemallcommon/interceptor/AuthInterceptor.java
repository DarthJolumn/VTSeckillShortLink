package com.jolumn.livemallcommon.interceptor;

import com.jolumn.livemallcommon.annotation.PublicApi;
import com.jolumn.livemallcommon.annotation.RequireAuth;
import com.jolumn.livemallcommon.annotation.RequireRole;
import com.jolumn.livemallcommon.constant.RoleEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final StringRedisTemplate redisTemplate;

    public AuthInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if (!(handler instanceof HandlerMethod hm)) return true;

        PublicApi publicApi = hm.getMethodAnnotation(PublicApi.class);
        RequireAuth requireAuth = hm.getMethodAnnotation(RequireAuth.class);
        RequireRole requireRole = hm.getMethodAnnotation(RequireRole.class);

        if (publicApi != null) return true;

        String userIdStr = request.getHeader("X-User-Id");
        Long userId = userIdStr != null ? Long.parseLong(userIdStr) : null;
        Integer role = request.getHeader("X-User-Role") != null
                ? Integer.parseInt(request.getHeader("X-User-Role")) : null;
        String deviceId = request.getHeader("X-Device-Id");

        if ((requireAuth != null || requireRole != null) && userId == null) {
            sendUnauthorized(response, "请先登录");
            return false;
        }

        // 设备在线检查：被踢设备即使 JWT 未过期也拒绝访问
        if (userId != null && deviceId != null && !deviceId.isBlank()) {
            Boolean inSession = redisTemplate.opsForSet()
                    .isMember("device_sessions:" + userId, deviceId);
            if (Boolean.FALSE.equals(inSession)) {
                sendUnauthorized(response, "设备已被踢下线");
                return false;
            }
        }

        if (requireRole != null) {
            if (role == null) {
                sendUnauthorized(response, "请先登录");
                return false;
            }
            RoleEnum userRole = RoleEnum.fromCode(role);
            if (!Arrays.asList(requireRole.value()).contains(userRole)) {
                sendForbidden(response, "权限不足");
                return false;
            }
        }

        return true;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) {
        try {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":401,\"message\":\"" + message + "\",\"data\":null,\"timestamp\":"
                            + System.currentTimeMillis() + "}");
        } catch (IOException e) {
            log.error("写入 401 响应失败", e);
        }
    }

    private void sendForbidden(HttpServletResponse response, String message) {
        try {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":403,\"message\":\"" + message + "\",\"data\":null,\"timestamp\":"
                            + System.currentTimeMillis() + "}");
        } catch (IOException e) {
            log.error("写入 403 响应失败", e);
        }
    }
}