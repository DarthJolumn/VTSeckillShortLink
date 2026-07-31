package com.jolumn.vtslproduct.service;

import com.jolumn.vtslcommon.codec.ShortCodeCodec;
import com.jolumn.vtslcommon.dto.PageResult;
import com.jolumn.vtslcommon.exception.BizException;
import com.jolumn.vtslcommon.util.SnowflakeIdGenerator;
import com.jolumn.vtslproduct.cache.ProductCacheService;
import com.jolumn.vtslproduct.dto.ProductDTO;
import com.jolumn.vtslproduct.dto.ProductPublishCmd;
import com.jolumn.vtslproduct.dto.ProductUpdateCmd;
import com.jolumn.vtslproduct.entity.Product;
import com.jolumn.vtslproduct.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class ProductFacadeImpl implements ProductFacade {

    private static final Logger log = LoggerFactory.getLogger(ProductFacadeImpl.class);
    private static final String DEDUP_PREFIX = "product:dedup:";

    private final ProductRepository productRepository;
    private final ProductCacheService cacheService;
    private final StringRedisTemplate redisTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final String shareUrlBase;

    public ProductFacadeImpl(ProductRepository productRepository,
                             ProductCacheService cacheService,
                             StringRedisTemplate redisTemplate,
                             SnowflakeIdGenerator snowflakeIdGenerator,
                             @Value("${product.share-url-base:https://s.livemall.com}") String shareUrlBase) {
        this.productRepository = productRepository;
        this.cacheService = cacheService;
        this.redisTemplate = redisTemplate;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.shareUrlBase = shareUrlBase;
    }

    @Override
    @Transactional
    public Long publish(Long userId, ProductPublishCmd cmd) {
        String dedupKey = DEDUP_PREFIX + userId + ":" + md5(cmd.getTitle());
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isNew)) {
            throw new BizException(400, "24h 内已发布同名商品");
        }

        Product product = new Product();
        product.setId(snowflakeIdGenerator.nextId());
        product.setUserId(userId);
        product.setTitle(cmd.getTitle());
        product.setSubtitle(cmd.getSubtitle());
        product.setMainImage(cmd.getMainImage());
        product.setDetailImages(cmd.getDetailImages());
        product.setPrice(cmd.getPrice());
        product.setStock(cmd.getStock());
        product.setStatus(1);
        product.setCategoryId(cmd.getCategoryId());
        product.setIsDeleted(0);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        productRepository.save(product);
        cacheService.put(product.getId(), product);

        log.info("商品发布成功: productId={}, userId={}, title={}", product.getId(), userId, cmd.getTitle());
        return product.getId();
    }

    @Override
    public ProductDTO getById(Long productId) {
        Product product = cacheService.getProduct(productId);
        if (product == null || product.getIsDeleted() != 0) {
            throw new BizException(404, "商品不存在");
        }
        return toDTO(product);
    }

    @Override
    @Transactional
    public boolean checkAndDecrStock(Long productId, int quantity) {
        int updated = productRepository.decrementStock(productId, quantity);
        if (updated > 0) {
            cacheService.refresh(productId);
            log.info("库存扣减成功: productId={}, quantity={}", productId, quantity);
            return true;
        }
        return false;
    }

    @Override
    public boolean checkAvailable(Long productId) {
        Product product = cacheService.getProduct(productId);
        if (product != null && product.getIsDeleted() == 0 && product.getStatus() == 1 && product.getStock() > 0) {
            return true;
        }
        return productRepository.findAvailableById(productId).isPresent();
    }

    @Override
    public PageResult<ProductDTO> list(int page, int size, Long userId, Long categoryId, String sortBy, String sortDir) {
        Sort sort;
        if ("price".equals(sortBy)) {
            sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by("price").ascending() : Sort.by("price").descending();
        } else {
            sort = Sort.by("createdAt").descending();
        }

        PageRequest pageRequest = PageRequest.of(page - 1, size, sort);

        Page<Product> productPage;
        if (userId != null) {
            productPage = productRepository.findByUserId(userId, pageRequest);
        } else if (categoryId != null) {
            productPage = productRepository.findAllActiveByCategory(categoryId, pageRequest);
        } else {
            productPage = productRepository.findAllActive(pageRequest);
        }

        return PageResult.of(
                productPage.getContent().stream().map(this::toDTO).toList(),
                (int) productPage.getTotalElements(),
                page,
                size
        );
    }

    @Override
    @Transactional
    public void update(Long id, Long userId, ProductUpdateCmd cmd) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "商品不存在"));
        if (!userId.equals(product.getUserId())) {
            throw new BizException(403, "无权修改该商品");
        }

        if (cmd.getTitle() != null) product.setTitle(cmd.getTitle());
        if (cmd.getSubtitle() != null) product.setSubtitle(cmd.getSubtitle());
        if (cmd.getMainImage() != null) product.setMainImage(cmd.getMainImage());
        if (cmd.getDetailImages() != null) product.setDetailImages(cmd.getDetailImages());
        if (cmd.getPrice() != null) product.setPrice(cmd.getPrice());
        if (cmd.getStock() != null) product.setStock(cmd.getStock());
        if (cmd.getCategoryId() != null) product.setCategoryId(cmd.getCategoryId());

        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
        cacheService.evict(id);
        log.info("商品更新成功: productId={}, userId={}", id, userId);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Long userId, Integer status) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "商品不存在"));
        if (!userId.equals(product.getUserId())) {
            throw new BizException(403, "无权操作该商品");
        }
        product.setStatus(status);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
        cacheService.evict(id);
        log.info("商品状态更新: productId={}, userId={}, status={}", id, userId, status);
    }

    @Override
    @Transactional
    public void softDelete(Long id, Long userId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "商品不存在"));
        if (!userId.equals(product.getUserId())) {
            throw new BizException(403, "无权删除该商品");
        }
        product.setIsDeleted(1);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
        cacheService.evict(id);
        log.info("商品已删除: productId={}, userId={}", id, userId);
    }

    private ProductDTO toDTO(Product product) {
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setUserId(product.getUserId());
        dto.setTitle(product.getTitle());
        dto.setSubtitle(product.getSubtitle());
        dto.setMainImage(product.getMainImage());
        dto.setDetailImages(product.getDetailImages());
        dto.setPrice(product.getPrice());
        dto.setStock(product.getStock());
        dto.setStatus(product.getStatus());
        dto.setCategoryId(product.getCategoryId());
        dto.setShareUrl(shareUrlBase + "/" + ShortCodeCodec.encodeProduct(product.getId()));
        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());
        return dto;
    }

    private String md5(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
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
