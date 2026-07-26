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

    private final ShortLinkRepository shortLinkRepository;
    private final ShortLinkCache shortLinkCache;
    private final IdGenerator idGenerator;
    private final StatisticsService statisticsService;
    private final int defaultExpireDays;

    @DubboReference
    private ProductShortLinkService productShortLinkService;

    public ShortLinkService(ShortLinkRepository shortLinkRepository,
                            ShortLinkCache shortLinkCache,
                            IdGenerator idGenerator,
                            StatisticsService statisticsService,
                            @Value("${shortlink.default-expire-days:30}") int defaultExpireDays) {
        this.shortLinkRepository = shortLinkRepository;
        this.shortLinkCache = shortLinkCache;
        this.idGenerator = idGenerator;
        this.statisticsService = statisticsService;
        this.defaultExpireDays = defaultExpireDays;
    }

    /**
     * 创建短链（公开接口，无 userId/title）
     */
    public String createShortLink(Long productId, String originalUrl) {
        return doCreate(productId, originalUrl, null, null);
    }

    /**
     * 创建短链（管理接口，带 userId/title）
     */
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

        log.info("短链创建成功: productId={}, shortCode={}, userId={}", productId, shortCode, userId);
        return shortCode;
    }

    /**
     * 获取原始 URL（三级解析：Redis → DB → 算法）
     * <p>
     * 解析策略：
     * 1. Redis 缓存命中：直接返回
     * 2. DB 命中：处理活动/直播间等非算法派生短链
     * 3. 算法推导：识别版本前缀 → 解码 productId → RPC 校验 → 回填缓存
     */
    public String getOriginalUrl(String shortCode) {
        // Level 1: L1 本地缓存
        String url = shortLinkCache.getFromL1(shortCode);
        if (url != null) {
            statisticsService.recordClick(shortCode);
            return url;
        }

        // Level 2: L2 Redis 缓存
        url = shortLinkCache.getFromL2(shortCode);
        if (url != null) {
            statisticsService.recordClick(shortCode);
            return url;
        }

        // Level 3a: DB 查询（非算法派生短链，如活动/直播间）
        if (!ShortCodeCodec.isProductShortCode(shortCode)) {
            ShortLink shortLink = shortLinkRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new BizException(404, "短链不存在"));

            Duration ttl = Duration.between(LocalDateTime.now(), shortLink.getExpireAt());
            if (ttl.isNegative() || ttl.isZero()) {
                throw new BizException(404, "短链已过期");
            }
            shortLinkCache.put(shortCode, shortLink.getOriginalUrl(), ttl);
            statisticsService.recordClick(shortCode);
            return shortLink.getOriginalUrl();
        }

        // Level 3b: 算法推导（商品短链）
        try {
            long productId = ShortCodeCodec.decode(shortCode);
            log.debug("算法推导短码: shortCode={}, productId={}", shortCode, productId);

            // RPC 校验商品状态并获取 URL
            String productUrl = productShortLinkService.getAvailableProductUrl(productId);
            if (productUrl == null) {
                throw new BizException(404, "商品不可售或已下架");
            }

            // 异步回填缓存（商品短链默认 10 年有效期）
            Duration ttl = Duration.ofDays(3650); // 10 年
            shortLinkCache.put(shortCode, productUrl, ttl);

            statisticsService.recordClick(shortCode);
            log.info("算法推导成功: shortCode={}, productId={}", shortCode, productId);
            return productUrl;
        } catch (IllegalArgumentException e) {
            log.warn("短码解码失败: shortCode={}", shortCode, e);
            throw new BizException(400, "无效的短码格式");
        }
    }

    /**
     * 分页查询用户的短链
     */
    public PageResult<ShortLinkVO> listByUser(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size);
        Page<ShortLink> pageResult = shortLinkRepository.findByUserId(userId, pageRequest);
        List<ShortLinkVO> records = pageResult.getContent().stream()
                .map(this::buildVO)
                .toList();
        return PageResult.of(records, (int) pageResult.getTotalElements(), page, size);
    }

    /**
     * 查询短链详情
     */
    public ShortLinkVO findById(Long id) {
        ShortLink entity = shortLinkRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "短链不存在"));
        return buildVO(entity);
    }

    /**
     * 软删除短链
     */
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
