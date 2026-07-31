package com.jolumn.vtslproduct.service;

import com.jolumn.vtslcommon.codec.ShortCodeCodec;
import com.jolumn.vtslcommon.dto.PageResult;
import com.jolumn.vtslcommon.dto.Result;
import com.jolumn.vtslcommon.exception.BizException;
import com.jolumn.vtslproduct.cache.ProductCacheService;
import com.jolumn.vtslproduct.dto.ProductDTO;
import com.jolumn.vtslproduct.dto.ProductPublishCmd;
import com.jolumn.vtslproduct.dto.ProductUpdateCmd;
import com.jolumn.vtslproduct.entity.Product;
import com.jolumn.vtslproduct.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductFacadeImplTest {

    private ProductRepository productRepo;
    private ProductCacheService cacheService;
    private StringRedisTemplate redisTemplate;
    private ProductFacadeImpl facade;

    @BeforeEach
    void setUp() {
        productRepo = mock(ProductRepository.class);
        cacheService = mock(ProductCacheService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        facade = new ProductFacadeImpl(productRepo, cacheService, redisTemplate,
                new com.jolumn.vtslcommon.util.SnowflakeIdGenerator(1L), "https://s.livemall.com");
    }

    @Test
    void publish_shouldReturnProductId() {
        when(redisTemplate.opsForValue().setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        when(productRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductPublishCmd cmd = new ProductPublishCmd();
        cmd.setTitle("Test Product");
        cmd.setPrice(BigDecimal.valueOf(99.99));
        cmd.setStock(100);

        Long productId = facade.publish(10L, cmd);

        assertThat(productId).isNotNull();
        assertThat(productId).isPositive();
    }

    @Test
    void publish_shouldRejectDuplicateWithin24h() {
        when(redisTemplate.opsForValue().setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(false);

        ProductPublishCmd cmd = new ProductPublishCmd();
        cmd.setTitle("Duplicate Product");
        cmd.setPrice(BigDecimal.valueOf(50));
        cmd.setStock(10);

        assertThatThrownBy(() -> facade.publish(10L, cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("24h 内已发布同名商品");
    }

    @Test
    void getById_shouldReturnProductWhenExists() {
        Product product = new Product();
        product.setId(1L);
        product.setTitle("Found Product");
        product.setStock(50);
        product.setStatus(1);
        product.setIsDeleted(0);

        when(cacheService.getProduct(1L)).thenReturn(product);

        ProductDTO dto = facade.getById(1L);

        assertThat(dto).isNotNull();
        assertThat(dto.getTitle()).isEqualTo("Found Product");
        assertThat(dto.getShareUrl()).contains("/product/");
    }

    @Test
    void getById_shouldThrowWhenProductNotExists() {
        when(cacheService.getProduct(999L)).thenReturn(null);

        assertThatThrownBy(() -> facade.getById(999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("商品不存在");
    }

    @Test
    void checkAvailable_shouldReturnTrue() {
        Product product = new Product();
        product.setId(1L);
        product.setStatus(1);
        product.setStock(10);
        product.setIsDeleted(0);

        when(cacheService.getProduct(1L)).thenReturn(product);

        assertThat(facade.checkAvailable(1L)).isTrue();
    }

    @Test
    void checkAvailable_shouldReturnFalseWhenDeleted() {
        Product product = new Product();
        product.setId(1L);
        product.setStatus(1);
        product.setStock(10);
        product.setIsDeleted(1);

        when(cacheService.getProduct(1L)).thenReturn(product);

        assertThat(facade.checkAvailable(1L)).isFalse();
    }

    @Test
    void list_shouldReturnPageResult() {
        Product product = new Product();
        product.setId(1L);
        product.setTitle("Listed Product");
        product.setPrice(BigDecimal.valueOf(29.99));
        product.setStock(100);
        product.setStatus(1);
        product.setIsDeleted(0);

        org.springframework.data.domain.Page<Product> mockPage =
                new org.springframework.data.domain.PageImpl<>(java.util.Collections.singletonList(product));
        when(productRepo.findAllActive(any())).thenReturn(mockPage);

        PageResult<ProductDTO> result = facade.list(1, 20, null, null, "createdAt", "desc");

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getTitle()).isEqualTo("Listed Product");
    }

    @Test
    void update_shouldEvictCache() {
        Product existing = new Product();
        existing.setId(1L);
        existing.setTitle("Old Title");
        existing.setUserId(10L);
        existing.setStock(10);
        existing.setStatus(1);
        existing.setIsDeleted(0);

        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));

        ProductUpdateCmd cmd = new ProductUpdateCmd();
        cmd.setTitle("New Title");

        facade.update(1L, 10L, cmd);

        verify(cacheService).evict(1L);
        assertThat(existing.getTitle()).isEqualTo("New Title");
    }

    @Test
    void update_shouldRejectUnauthorizedUser() {
        Product existing = new Product();
        existing.setId(1L);
        existing.setUserId(10L);

        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));

        ProductUpdateCmd cmd = new ProductUpdateCmd();

        assertThatThrownBy(() -> facade.update(1L, 99L, cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权修改该商品");
    }

    @Test
    void softDelete_shouldMarkAsDeleted() {
        Product existing = new Product();
        existing.setId(1L);
        existing.setUserId(10L);
        existing.setIsDeleted(0);

        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));

        facade.softDelete(1L, 10L);

        assertThat(existing.getIsDeleted()).isEqualTo(1);
        verify(cacheService).evict(1L);
    }

    @Test
    void softDelete_shouldRejectUnauthorizedUser() {
        Product existing = new Product();
        existing.setId(1L);
        existing.setUserId(10L);

        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> facade.softDelete(1L, 99L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权删除该商品");
    }
}