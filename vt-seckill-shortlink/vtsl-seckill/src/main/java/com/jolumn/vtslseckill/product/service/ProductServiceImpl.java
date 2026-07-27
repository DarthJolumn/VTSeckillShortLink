package com.jolumn.vtslseckill.product.service;

import com.jolumn.vtslcommon.codec.ShortCodeCodec;
import com.jolumn.vtslcommon.dto.PageResult;
import com.jolumn.vtslcommon.exception.BizException;
import com.jolumn.vtslcommon.util.SnowflakeIdGenerator;
import com.jolumn.vtslseckill.product.dto.ProductDTO;
import com.jolumn.vtslseckill.product.dto.ProductPublishCmd;
import com.jolumn.vtslseckill.product.dto.ProductUpdateCmd;
import com.jolumn.vtslseckill.product.entity.Product;
import com.jolumn.vtslseckill.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class ProductServiceImpl implements ProductFacade {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);
    private static final String DEDUP_PREFIX = "product:dedup:";
    private static final String STOCK_PREFIX = "product:stock:";

    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final String shareUrlBase;

    public ProductServiceImpl(ProductRepository productRepository,
                              StringRedisTemplate redisTemplate,
                              SnowflakeIdGenerator snowflakeIdGenerator,
                              @Value("${product.share-url-base:https://s.livemall.com}") String shareUrlBase) {
        this.productRepository = productRepository;
        this.redisTemplate = redisTemplate;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.shareUrlBase = shareUrlBase;
    }

    @Override
    @Transactional
    public Long publish(ProductPublishCmd cmd) {
        String dedupKey = DEDUP_PREFIX + cmd.getUserId() + ":" + md5(cmd.getTitle());
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isNew)) {
            throw new BizException(400, "24h 内已发布同名商品");
        }

        Product product = new Product();
        product.setUserId(cmd.getUserId());
        product.setTitle(cmd.getTitle());
        product.setSubtitle(cmd.getSubtitle());
        product.setMainImage(cmd.getMainImage());
        product.setDetailImages(cmd.getDetailImages());
        product.setPrice(cmd.getPrice());
        product.setStock(cmd.getStock());
        product.setStatus(1);
        product.setCategoryId(cmd.getCategoryId());
        product.setId(snowflakeIdGenerator.nextId());
        product.setIsDeleted(0);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        // 3. 保存（V1 单表手动设雪花 ID，后续 ShardingSphere 接管后移除 setter）
        productRepository.save(product);

        String stockKey = STOCK_PREFIX + product.getId();
        redisTemplate.opsForValue().set(stockKey, String.valueOf(cmd.getStock()));

        log.info("商品发布成功: productId={}, userId={}, title={}", product.getId(), cmd.getUserId(), cmd.getTitle());
        return product.getId();
    }

    @Override
    public ProductDTO getById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BizException(404, "商品不存在"));
        return toDTO(product);
    }

    @Override
    @Transactional
    public boolean checkAndDecrStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BizException(404, "商品不存在"));

        if (product.getStatus() != 1 || product.getIsDeleted() != 0) {
            throw new BizException(400, "商品不可售");
        }

        if (product.getStock() < quantity) {
            return false;
        }

        product.setStock(product.getStock() - quantity);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        String stockKey = STOCK_PREFIX + productId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStock()));

        log.info("库存扣减成功: productId={}, quantity={}, remain={}", productId, quantity, product.getStock());
        return true;
    }

    @Override
    public boolean checkAvailable(Long productId) {
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
        if (cmd.getStock() != null) {
            product.setStock(cmd.getStock());
            String stockKey = STOCK_PREFIX + id;
            redisTemplate.opsForValue().set(stockKey, String.valueOf(cmd.getStock()));
        }
        if (cmd.getCategoryId() != null) product.setCategoryId(cmd.getCategoryId());

        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
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
