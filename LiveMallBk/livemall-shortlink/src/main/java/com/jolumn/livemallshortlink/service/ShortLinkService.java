package com.jolumn.livemallshortlink.service;

import com.jolumn.livemallcommon.api.ProductShortLinkService;
import com.jolumn.livemallcommon.codec.ShortCodeCodec;
import com.jolumn.livemallcommon.dto.PageResult;
import com.jolumn.livemallcommon.exception.BizException;
import com.jolumn.livemallshortlink.dto.ShortLinkVO;
import com.jolumn.livemallshortlink.entity.ShortLink;
import com.jolumn.livemallshortlink.repository.ShortLinkRepository;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShortLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkService.class);
    private static final String BASE_SHORT_URL = "https://s.livemall.com/";
    private static final String STATS_CNT_PREFIX = "stats:cnt:";
    private static final String LOCK_PREFIX = "s:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final int SPIN_MAX_RETRIES = 20;
    private static final Duration SPIN_INTERVAL = Duration.ofMillis(50);

    private final ShortLinkRepository shortLinkRepository;
    private final ShortLinkCache shortLinkCache;
    private final IdGenerator idGenerator;
    private final StatisticsService statisticsService;
    private final StringRedisTemplate redisTemplate;
    private final int defaultExpireDays;

    @DubboReference
    private ProductShortLinkService productShortLinkService;

    public ShortLinkService(ShortLinkRepository shortLinkRepository,
                            ShortLinkCache shortLinkCache,
                            IdGenerator idGenerator,
                            StatisticsService statisticsService,
                            StringRedisTemplate redisTemplate,
                            @Value("${shortlink.default-expire-days:30}") int defaultExpireDays) {
        this.shortLinkRepository = shortLinkRepository;
        this.shortLinkCache = shortLinkCache;
        this.idGenerator = idGenerator;
        this.statisticsService = statisticsService;
        this.redisTemplate = redisTemplate;
        this.defaultExpireDays = defaultExpireDays;
    }

    public String createShortLink(Long productId, String originalUrl) {
        return doCreate(productId, originalUrl, null, null);
    }

    @Transactional
    public ShortLinkVO createShortLink(Long userId, Long productId, String originalUrl, String title) {
        String shortCode = doCreate(productId, originalUrl, userId, title);
        return buildVO(shortLinkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new BizException(500, "短链创建后查询失败")));
    }

    private String doCreate(Long productId, String originalUrl, Long userId, String title) {
        String urlHash = md5(originalUrl);
        Duration hashTtl = Duration.ofHours(24);

        String existingCode = shortLinkCache.getExistingCodeByHash(urlHash);
        if (existingCode != null) {
            log.info("短链已存在(Redis)，直接返回: productId={}, shortCode={}", productId, existingCode);
            return existingCode;
        }

        ShortLink existing = shortLinkRepository.findByUrlHash(urlHash).orElse(null);
        if (existing != null) {
            shortLinkCache.putHashMapping(urlHash, existing.getShortCode(), hashTtl);
            log.info("短链 DB 已存在，回填缓存: productId={}, shortCode={}", productId, existing.getShortCode());
            return existing.getShortCode();
        }

        String shortCode = generateWithRetry(productId, originalUrl, userId, title, urlHash);

        // Cache Aside: 只写 DB，Redis 缓存由读路径回填
        shortLinkCache.putHashMapping(urlHash, shortCode, hashTtl);

        log.info("短链创建成功: productId={}, shortCode={}, userId={}", productId, shortCode, userId);
        return shortCode;
    }

    private String generateWithRetry(Long productId, String originalUrl, Long userId, String title, String urlHash) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            String shortCode = idGenerator.nextCode();
            LocalDateTime expireAt = LocalDateTime.now().plusDays(defaultExpireDays);

            ShortLink shortLink = new ShortLink();
            shortLink.setShortCode(shortCode);
            shortLink.setUserId(userId);
            shortLink.setTitle(title);
            shortLink.setProductId(productId);
            shortLink.setOriginalUrl(originalUrl);
            shortLink.setUrlHash(urlHash);
            shortLink.setExpireAt(expireAt);

            try {
                shortLinkRepository.save(shortLink);
                return shortCode;
            } catch (DuplicateKeyException e) {
                log.warn("短码冲突，重试第{}次: shortCode={}", i + 1, shortCode);
            }
        }
        throw new BizException(500, "短码生成失败，请重试");
    }

    /**
     * 获取原始 URL（三级链路 + DCL 防击穿）
     *
     * L1 Caffeine → L2 Redis Hash → DCL 锁 → fallback（DB/RPC）→ 回填缓存
     */
    public String getOriginalUrl(String shortCode) {
        if (shortLinkCache.isBlocked(shortCode)) {
            throw new BizException(403, "短链已被封禁");
        }

        String url = shortLinkCache.getFromL1(shortCode);
        if (url != null) {
            incrementClickCount(shortCode);
            return url;
        }

        url = shortLinkCache.getFromL2(shortCode);
        if (url != null) {
            incrementClickCount(shortCode);
            return url;
        }

        // DCL：分布式锁防缓存击穿（SETNX 5s TTL）
        String lockKey = LOCK_PREFIX + shortCode;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (Boolean.TRUE.equals(locked)) {
            try {
                url = resolveFallback(shortCode);
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            url = spinWaitForCache(shortCode);
        }

        incrementClickCount(shortCode);
        return url;
    }

    /**
     * 自旋等待其他线程回填缓存
     */
    private String spinWaitForCache(String shortCode) {
        for (int i = 0; i < SPIN_MAX_RETRIES; i++) {
            try {
                Thread.sleep(SPIN_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            String url = shortLinkCache.getFromL1(shortCode);
            if (url != null) return url;
            url = shortLinkCache.getFromL2(shortCode);
            if (url != null) return url;
        }
        // 自旋超时，尝试直接兜底（double-check，多数情况不会走到这里）
        log.warn("DCL 自旋超时，直接兜底: shortCode={}", shortCode);
        return resolveFallback(shortCode);
    }

    /**
     * 兜底：算法码走 Dubbo RPC，非算法码查 DB。
     * Miss 时异步补偿回填 Redis。
     */
    private String resolveFallback(String shortCode) {
        if (ShortCodeCodec.isProductShortCode(shortCode)) {
            return resolveProductShortCode(shortCode);
        }
        return resolveDbShortCode(shortCode);
    }

    private String resolveProductShortCode(String shortCode) {
        try {
            long productId = ShortCodeCodec.decode(shortCode);
            String productUrl = productShortLinkService.getAvailableProductUrl(productId);
            if (productUrl == null) {
                throw new BizException(404, "商品不可售或已下架");
            }
            Duration ttl = Duration.ofDays(3650);
            shortLinkCache.putWithProduct(shortCode, productUrl, productId, ttl);
            log.info("算法推导: shortCode={}, productId={}", shortCode, productId);
            return productUrl;
        } catch (IllegalArgumentException e) {
            log.warn("短码解码失败: shortCode={}", shortCode, e);
            throw new BizException(400, "无效的短码格式");
        }
    }

    private String resolveDbShortCode(String shortCode) {
        ShortLink shortLink = shortLinkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new BizException(404, "短链不存在"));

        Duration ttl = Duration.between(LocalDateTime.now(), shortLink.getExpireAt());
        if (ttl.isNegative() || ttl.isZero()) {
            throw new BizException(404, "短链已过期");
        }
        shortLinkCache.put(shortCode, shortLink.getOriginalUrl(), ttl);
        log.info("DB 兜底查询: shortCode={}", shortCode);
        return shortLink.getOriginalUrl();
    }

    public PageResult<ShortLinkVO> listByUser(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size);
        Page<ShortLink> pageResult = shortLinkRepository.findByUserId(userId, pageRequest);
        List<ShortLinkVO> records = pageResult.getContent().stream()
                .map(this::buildVO)
                .toList();
        return PageResult.of(records, (int) pageResult.getTotalElements(), page, size);
    }

    public ShortLinkVO findById(Long id) {
        ShortLink entity = shortLinkRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "短链不存在"));
        return buildVO(entity);
    }

    @Transactional
    public void softDelete(Long id, Long userId) {
        ShortLink entity = shortLinkRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "短链不存在"));
        if (!userId.equals(entity.getUserId())) {
            throw new BizException(403, "无权删除该短链");
        }
        entity.setStatus(2);
        shortLinkRepository.save(entity);
        log.info("短链已软删除: id={}, shortCode={}, userId={}", id, entity.getShortCode(), userId);
    }

    private void incrementClickCount(String shortCode) {
        statisticsService.recordClick(shortCode);
        shortLinkCache.incrementClickCount(shortCode);
    }

    private ShortLinkVO buildVO(ShortLink entity) {
        ShortLinkVO vo = new ShortLinkVO();
        vo.setId(entity.getId());
        vo.setShortCode(entity.getShortCode());
        vo.setShortUrl(BASE_SHORT_URL + entity.getShortCode());
        vo.setTitle(entity.getTitle());
        vo.setProductId(entity.getProductId());
        vo.setOriginalUrl(entity.getOriginalUrl());
        vo.setClickCount(entity.getClickCount());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setExpireAt(entity.getExpireAt());
        return vo;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败", e);
        }
    }
}
