package com.jolumn.vtslshortlinkapi.controller;

import com.jolumn.vtslcommon.annotation.PublicApi;
import com.jolumn.vtslcommon.annotation.RequireAuth;
import com.jolumn.vtslcommon.context.UserContext;
import com.jolumn.vtslcommon.dto.Result;
import com.jolumn.vtslcommon.exception.BizException;
import com.jolumn.vtslshortlinkapi.dto.request.CreateUrlRequest;
import com.jolumn.vtslshortlinkapi.dto.request.UpdateUrlRequest;
import com.jolumn.vtslshortlinkapi.dto.response.*;
import com.jolumn.vtslshortlinkapi.service.AnalyticsService;
import com.jolumn.vtslshortlinkapi.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class UrlController {

    private final UrlService urlService;
    private final AnalyticsService analyticsService;

    public UrlController(UrlService urlService, AnalyticsService analyticsService) {
        this.urlService = urlService;
        this.analyticsService = analyticsService;
    }

    @PostMapping("/url/shorten")
    @RequireAuth
    public Result<CreateUrlResponse> create(@Valid @RequestBody CreateUrlRequest request) {
        Long userId = requireUserId();
        return Result.ok(urlService.create(userId, request));
    }

    @GetMapping("/url/")
    @RequireAuth
    public Result<List<UrlDetailResponse>> list() {
        Long userId = requireUserId();
        return Result.ok(urlService.listMine(userId));
    }

    @GetMapping("/url/{shortKey}")
    @RequireAuth
    public Result<UrlDetailResponse> details(@PathVariable String shortKey) {
        Long userId = requireUserId();
        return Result.ok(urlService.details(userId, shortKey));
    }

    @PatchMapping("/url/{shortKey}")
    @RequireAuth
    public Result<UrlUpdateResponse> update(@PathVariable String shortKey,
                                            @Valid @RequestBody UpdateUrlRequest request) {
        Long userId = requireUserId();
        return Result.ok(urlService.update(userId, shortKey, request));
    }

    @DeleteMapping("/url/{shortKey}")
    @RequireAuth
    public Result<Void> delete(@PathVariable String shortKey) {
        Long userId = requireUserId();
        urlService.delete(userId, shortKey);
        return Result.ok();
    }

    @GetMapping("/url/redirect/{shortKey}")
    @PublicApi
    public Result<String> redirect(@PathVariable String shortKey, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");
        String originalUrl = urlService.redirect(shortKey, clientIp, userAgent, referer);
        return Result.ok(originalUrl);
    }

    @GetMapping("/analytics/{urlId}")
    @RequireAuth
    public Result<List<AnalyticsItem>> analytics(@PathVariable String urlId) {
        Long userId = requireUserId();
        return Result.ok(analyticsService.getAnalytics(userId, urlId));
    }

    private Long requireUserId() {
        Long userId = UserContext.currentUserId();
        if (userId == null) {
            throw new BizException(401, "Unauthorized: Id is missing from context");
        }
        return userId;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
