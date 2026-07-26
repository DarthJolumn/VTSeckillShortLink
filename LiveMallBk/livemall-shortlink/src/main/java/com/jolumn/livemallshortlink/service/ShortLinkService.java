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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ShortLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkService.class);
    private static final String BASE_SHORT_URL = "https://s.livemall.com/";
    private static final String STATS_CNT_PREFIX = "stats:cnt:";
    private static final String LOCK_PREFIX = "s:lock:";
    private static final String LOCK_UNLOCK_LUA =
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";

    private final ShortLinkRepository shortLinkRepository;
    private final ShortLinkCache shortLinkCache;
    private final IdGenerator idGenerator;
    private final StatisticsService statisticsService;
    private final StringRedisTemplate redisTemplate;
    private final ShortCodeBloomFilter bloomFilter;
    private final int defaultExpireDays;
    private final DefaultRedisScript<Long> unlockScript;

    @DubboReference
    private ProductShortLinkService productShortLinkService;

    public ShortLinkService(ShortLinkRepository shortLinkRepository,
                            ShortLinkCache shortLinkCache,
                            IdGenerator idGenerator,
                            StatisticsService statisticsService,
                            StringRedisTemplate redisTemplate,
                            ShortCodeBloomFilter bloomFilter,
                            @Value("${shortlink.default-expire-days:30}") int defaultExpireDays) {
        this.shortLinkRepository = shortLinkRepository;
        this.shortLinkCache = shortLinkCache;
        this.idGenerator = idGenerator;
        this.statisticsService = statisticsService;
        this.redisTemplate = redisTemplate;
        this.bloomFilter = bloomFilter;
        this.defaultExpireDays = defaultExpireDays;
        this.unlockScript = new DefaultRedisScript<>(LOCK_UNLOCK_LUA, Long.class);
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

        String existingCode = shortLinkCache.getExistingCodeByHash(urlHash);
        if (existingCode != null) {
            log.info("短链已存在，直接返回: productId={}, shortCode={}", productId, existingCode);
            return existingCode;
        }

        ShortLink existing = shortLinkRepository.findByUrlHash(urlHash).orElse(null);
        if (existing != null) {
            shortLinkCache.putHashMapping(urlHash, existing.getShortCode());
            log.info("短链 DB 已存在，回填缓存: productId={}, shortCode={}", productId, existing.getShortCode());
            return existing.getShortCode();
        }

        String shortCode = idGenerator.nextCode();
        LocalDateTime expireAt = LocalDateTime.now().plusDays(defaultExpireDays);
        Duration ttl = Duration.ofDays(defaultExpireDays);

        ShortLink shortLink = new ShortLink();
        shortLink.setShortCode(shortCode);
        shortLink.setUserId(userId);
        shortLink.setTitle(title);
        shortLink.setProductId(productId);
        shortLink.setOriginalUrl(originalUrl);
        shortLink.setUrlHash(urlHash);
        shortLink.setExpireAt(expireAt);
        shortLinkRepository.save(shortLink);

        shortLinkCache.put(shortCode, originalUrl, ttl);
        shortLinkCache.putHashMapping(urlHash, shortCode);
        bloomFilter.add(shortCode);

        log.info("短链创建成功: productId={}, shortCode={}, userId={}", productId, shortCode, userId);
        return shortCode;
    }

    /**
     * 获取原始 URL（五级防御：BloomFilter → L1 → L2 → DCL → DB/算法）
     * <p>
     * 防御链路：
     * 1. BloomFilter 穿透防御（非算法码）
     * 2. Caffeine L1 本地缓存
     * 3. Redis L2 缓存 + 空值标记
     * 4. Redisson DCL（SETNX 互斥锁 + 双重检查）
     * 5. DB 查询 / 算法推导（兜底）
     */
    public String getOriginalUrl(String shortCode) {
        // Level 0: BloomFilter 快速拒绝（仅非算法码）
        if (!ShortCodeCodec.isProductShortCode(shortCode)
                && !bloomFilter.mightContain(shortCode)) {
            throw new BizException(404, "短链不存在");
        }

        // Level 1: L1 本地缓存
        String url = shortLinkCache.getFromL1(shortCode);
        if (url != null) {
            incrementClickCount(shortCode);
            return url;
        }

        // Level 2: L2 Redis 缓存
        url = shortLinkCache.getFromL2(shortCode);
        if (url != null) {
            incrementClickCount(shortCode);
            return url;
        }

        // Level 3: DCL — Redis 分布式锁 + 双重检查
        url = resolveWithLock(shortCode);
        incrementClickCount(shortCode);
        return url;
    }

    /** DCL：SETNX 互斥锁 + 双重检查 L2 + 指数退避重试 */
    private String resolveWithLock(String shortCode) {
        String lockKey = LOCK_PREFIX + shortCode;
        String lockValue = UUID.randomUUID().toString();
        int maxRetries = 10;
        long retryIntervalMs = 50;

        for (int i = 0; i < maxRetries; i++) {
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(10));

            if (Boolean.TRUE.equals(locked)) {
                try {
                    // 双重检查 L2
                    String url = shortLinkCache.getFromL2(shortCode);
                    if (url != null) return url;

                    // 兜底查询
                    return resolveFromDbOrAlgorithm(shortCode);
                } finally {
                    // Lua 安全解锁：仅释放自己持有的锁
                    redisTemplate.execute(unlockScript, List.of(lockKey), lockValue);
                }
            }

            // 未获取到锁，等待后重试（指数退避）
            try {
                Thread.sleep(retryIntervalMs);
                retryIntervalMs = Math.min(retryIntervalMs * 2, 500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // 每次重试都检查 L2，看锁持有者是否已经回填
            String url = shortLinkCache.getFromL2(shortCode);
            if (url != null) return url;
        }

        // 所有重试都失败，直接兜底查询
        return resolveFromDbOrAlgorithm(shortCode);
    }

    /** 兜底查询：DB 非算法码 → 算法推导商品码 */
    private String resolveFromDbOrAlgorithm(String shortCode) {
        if (!ShortCodeCodec.isProductShortCode(shortCode)) {
            ShortLink shortLink = shortLinkRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new BizException(404, "短链不存在"));

            Duration ttl = Duration.between(LocalDateTime.now(), shortLink.getExpireAt());
            if (ttl.isNegative() || ttl.isZero()) {
                throw new BizException(404, "短链已过期");
            }
            shortLinkCache.put(shortCode, shortLink.getOriginalUrl(), ttl);
            log.info("DB 查询: shortCode={}", shortCode);
            return shortLink.getOriginalUrl();
        }

        try {
            long productId = ShortCodeCodec.decode(shortCode);
            String productUrl = productShortLinkService.getAvailableProductUrl(productId);
            if (productUrl == null) {
                throw new BizException(404, "商品不可售或已下架");
            }
            Duration ttl = Duration.ofDays(3650);
            shortLinkCache.put(shortCode, productUrl, ttl);
            log.info("算法推导: shortCode={}, productId={}", shortCode, productId);
            return productUrl;
        } catch (IllegalArgumentException e) {
            log.warn("短码解码失败: shortCode={}", shortCode, e);
            throw new BizException(400, "无效的短码格式");
        }
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
        redisTemplate.opsForValue().increment(STATS_CNT_PREFIX + shortCode);
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
