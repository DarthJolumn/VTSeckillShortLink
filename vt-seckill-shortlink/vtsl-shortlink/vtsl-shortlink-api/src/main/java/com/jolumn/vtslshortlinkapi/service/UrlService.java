package com.jolumn.vtslshortlinkapi.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jolumn.vtslcommon.exception.BizException;
import com.jolumn.vtslshortlinkapi.dto.request.CreateUrlRequest;
import com.jolumn.vtslshortlinkapi.dto.request.UpdateUrlRequest;
import com.jolumn.vtslshortlinkapi.dto.response.*;
import com.jolumn.vtslshortlinkapi.entity.Url;
import com.jolumn.vtslshortlinkapi.grpc.KeyServiceClient;
import com.jolumn.vtslshortlinkapi.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);
    private static final String CACHE_PREFIX = "url:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final UrlRepository urlRepository;
    private final KeyServiceClient keyServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final AnalyticsAsyncService analyticsAsyncService;

    private final Cache<String, String> localCache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(3, TimeUnit.SECONDS)
            .build();

    public UrlService(UrlRepository urlRepository,
                      KeyServiceClient keyServiceClient,
                      StringRedisTemplate redisTemplate,
                      AnalyticsAsyncService analyticsAsyncService) {
        this.urlRepository = urlRepository;
        this.keyServiceClient = keyServiceClient;
        this.redisTemplate = redisTemplate;
        this.analyticsAsyncService = analyticsAsyncService;
    }

    @Transactional
    public CreateUrlResponse create(Long userId, CreateUrlRequest req) {
        if (urlRepository.existsByOriginalUrlAndUserIdAndDeletedAtIsNull(req.getOriginalUrl(), userId)) {
            throw new BizException(409, "This URL has already been shortened.");
        }

        String shortKey = req.getShortKey();
        if (shortKey == null || shortKey.isBlank()) {
            try {
                shortKey = keyServiceClient.getKey();
            } catch (Exception e) {
                log.error("Failed to get key from KGS", e);
                throw new BizException(500, "Failed to generate short key");
            }
        }

        if (urlRepository.existsByShortKeyAndDeletedAtIsNull(shortKey)) {
            throw new BizException(409, "The generated ShortKey already exists, please try again");
        }

        Url url = new Url();
        url.setOriginalUrl(req.getOriginalUrl());
        url.setShortKey(shortKey);
        url.setTitle(req.getTitle());
        url.setUserId(userId);
        urlRepository.save(url);

        cacheUrlAsync(url);

        log.info("URL created: shortKey={}, userId={}", shortKey, userId);
        return CreateUrlResponse.from(url);
    }

    public List<UrlDetailResponse> listMine(Long userId) {
        return urlRepository.findByUserIdAndDeletedAtIsNull(userId)
                .stream()
                .map(UrlDetailResponse::from)
                .toList();
    }

    public UrlDetailResponse details(Long userId, String shortKey) {
        Url url = findUrlWithOwnership(userId, shortKey);
        return UrlDetailResponse.from(url);
    }

    @Transactional
    public UrlUpdateResponse update(Long userId, String shortKey, UpdateUrlRequest req) {
        Url url = findUrlWithOwnership(userId, shortKey);

        boolean keyChanged = false;
        if (req.getShortUrl() != null && !req.getShortUrl().isBlank() && !req.getShortUrl().equals(shortKey)) {
            if (urlRepository.existsByShortKeyAndDeletedAtIsNull(req.getShortUrl())) {
                throw new BizException(409, "This short key already exists.");
            }
            url.setShortKey(req.getShortUrl());
            keyChanged = true;
        }
        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            url.setTitle(req.getTitle());
        }
        urlRepository.save(url);

        String oldKey = keyChanged ? shortKey : url.getShortKey();
        evictCache(oldKey);

        return UrlUpdateResponse.from(url);
    }

    @Transactional
    public void delete(Long userId, String shortKey) {
        Url url = findUrlWithOwnership(userId, shortKey);
        url.setDeletedAt(java.time.LocalDateTime.now());
        urlRepository.save(url);
        evictCache(shortKey);
    }

    public String redirect(String shortKey, String clientIp, String userAgent, String referer) {
        String originalUrl = getFromCache(shortKey);

        if (originalUrl == null) {
            Url url = urlRepository.findByShortKeyAndDeletedAtIsNull(shortKey)
                    .orElseThrow(() -> new BizException(404, "URL not found"));
            originalUrl = url.getOriginalUrl();
            cacheUrl(url);
            analyticsAsyncService.record(url.getId(), clientIp, userAgent, referer);
        } else {
            Url url = urlRepository.findByShortKeyAndDeletedAtIsNull(shortKey).orElse(null);
            if (url != null) {
                analyticsAsyncService.record(url.getId(), clientIp, userAgent, referer);
            }
        }

        return originalUrl;
    }

    private Url findUrlWithOwnership(Long userId, String shortKey) {
        Url url = urlRepository.findByShortKeyAndDeletedAtIsNull(shortKey)
                .orElseThrow(() -> new BizException(404, "URL not found"));
        if (!userId.equals(url.getUserId())) {
            throw new BizException(404, "URL not found");
        }
        return url;
    }

    private String getFromCache(String shortKey) {
        String url = localCache.getIfPresent(shortKey);
        if (url != null) return url;

        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + shortKey);
        if (cached != null) {
            localCache.put(shortKey, cached);
            return cached;
        }
        return null;
    }

    private void cacheUrl(Url url) {
        localCache.put(url.getShortKey(), url.getOriginalUrl());
        redisTemplate.opsForValue().set(CACHE_PREFIX + url.getShortKey(), url.getOriginalUrl(), CACHE_TTL);
    }

    private void cacheUrlAsync(Url url) {
        try {
            cacheUrl(url);
        } catch (Exception e) {
            log.error("Failed to cache URL: {}", url.getShortKey(), e);
        }
    }

    private void evictCache(String shortKey) {
        localCache.invalidate(shortKey);
        redisTemplate.delete(CACHE_PREFIX + shortKey);
    }
}
