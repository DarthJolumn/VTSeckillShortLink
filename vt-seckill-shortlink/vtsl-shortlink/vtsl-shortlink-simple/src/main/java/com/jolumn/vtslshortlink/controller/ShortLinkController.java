package com.jolumn.vtslshortlink.controller;

import com.jolumn.vtslcommon.annotation.RequireAuth;
import com.jolumn.vtslcommon.context.UserContext;
import com.jolumn.vtslcommon.dto.PageResult;
import com.jolumn.vtslcommon.dto.Result;
import com.jolumn.vtslcommon.exception.BizException;
import com.jolumn.vtslshortlink.dto.ShortLinkVO;
import com.jolumn.vtslshortlink.service.RateLimitService;
import com.jolumn.vtslshortlink.service.ShortLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/s")
public class ShortLinkController {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkController.class);
    private static final int SHORT_CODE_RATE_LIMIT = 1000;
    private static final int SHORT_CODE_RATE_WINDOW = 60;
    private static final int IP_RATE_LIMIT = 100;
    private static final int IP_RATE_WINDOW = 60;

    private final ShortLinkService shortLinkService;
    private final RateLimitService rateLimitService;

    public ShortLinkController(ShortLinkService shortLinkService,
                               RateLimitService rateLimitService) {
        this.shortLinkService = shortLinkService;
        this.rateLimitService = rateLimitService;
    }

    // ===================== 公开接口 =====================

    /**
     * 创建短链（公开）
     * POST /s/create
     */
    @PostMapping("/create")
    public Result<Map<String, String>> createShortLink(@Valid @RequestBody CreateShortLinkRequest request) {
        String shortCode = shortLinkService.createShortLink(request.getProductId(), request.getOriginalUrl());
        return Result.ok(Map.of("shortCode", shortCode, "shortUrl", "https://s.livemall.com/" + shortCode));
    }

    /**
     * 解析短链（公开，短码维度 1000次/分钟 + IP维度 100次/分钟）
     * GET /s/{shortCode} → 返回原始 URL，前端自行跳转
     */
    @GetMapping("/{shortCode}")
    public Result<Map<String, String>> resolve(@PathVariable String shortCode, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String ipLimitKey = "s:ratelimit:ip:" + clientIp;
        if (!rateLimitService.tryAcquire(ipLimitKey, IP_RATE_LIMIT, IP_RATE_WINDOW)) {
            throw new BizException(429, "请求过于频繁，请稍后再试");
        }

        String codeLimitKey = "s:ratelimit:code:" + shortCode;
        if (!rateLimitService.tryAcquire(codeLimitKey, SHORT_CODE_RATE_LIMIT, SHORT_CODE_RATE_WINDOW)) {
            throw new BizException(429, "该短链访问过于频繁，请稍后再试");
        }

        String originalUrl = shortLinkService.getOriginalUrl(shortCode);
        return Result.ok(Map.of("shortCode", shortCode, "originalUrl", originalUrl));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    // ===================== 管理接口（需登录） =====================

    /**
     * 创建短链（管理）
     * POST /s/manage/create
     */
    @PostMapping("/manage/create")
    @RequireAuth
    public Result<Map<String, Object>> createManageLink(@Valid @RequestBody CreateManageLinkRequest request) {
        Long userId = UserContext.currentUserId();
        ShortLinkVO vo = shortLinkService.createShortLink(userId, request.getProductId(), request.getOriginalUrl(), request.getTitle());
        return Result.ok(Map.of(
                "id", vo.getId(),
                "shortCode", vo.getShortCode(),
                "shortUrl", vo.getShortUrl()));
    }

    /**
     * 分页查询短链列表
     * GET /s/manage/list?page=1&size=20
     */
    @GetMapping("/manage/list")
    @RequireAuth
    public Result<PageResult<ShortLinkVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.currentUserId();
        return Result.ok(shortLinkService.listByUser(userId, page, size));
    }

    /**
     * 短链详情
     * GET /s/manage/{id}
     */
    @GetMapping("/manage/{id}")
    @RequireAuth
    public Result<ShortLinkVO> detail(@PathVariable Long id) {
        return Result.ok(shortLinkService.findById(id));
    }

    /**
     * 软删除短链
     * DELETE /s/manage/{id}
     */
    @DeleteMapping("/manage/{id}")
    @RequireAuth
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.currentUserId();
        shortLinkService.softDelete(id, userId);
        return Result.ok();
    }

    // ===================== DTO =====================

    public static class CreateShortLinkRequest {
        @NotNull(message = "商品 ID 不能为空")
        private Long productId;

        @NotBlank(message = "原始 URL 不能为空")
        private String originalUrl;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getOriginalUrl() { return originalUrl; }
        public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }
    }

    public static class CreateManageLinkRequest {
        @NotNull(message = "商品 ID 不能为空")
        private Long productId;

        @NotBlank(message = "原始 URL 不能为空")
        private String originalUrl;

        private String title;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getOriginalUrl() { return originalUrl; }
        public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }
}
