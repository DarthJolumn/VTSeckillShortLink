package com.jolumn.vtslproduct.cache;

import com.jolumn.vtslproduct.entity.Product;
import com.jolumn.vtslproduct.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCacheServiceTest {

    private ProductRepository productRepo;
    private ProductCacheService cacheService;

    @BeforeEach
    void setUp() {
        productRepo = mock(ProductRepository.class);
        cacheService = new ProductCacheService(productRepo, 5, 2000);
    }

    @Test
    void shouldReturnCachedProduct() {
        Product product = new Product();
        product.setId(1L);
        product.setTitle("Test Product");
        product.setStock(100);
        product.setStatus(1);
        product.setIsDeleted(0);

        cacheService.put(1L, product);
        Product result = cacheService.getProduct(1L);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Test Product");
    }

    @Test
    void shouldReturnNullForMissingProduct() {
        Product result = cacheService.getProduct(999L);
        assertThat(result).isNull();
    }

    @Test
    void shouldEvictProduct() {
        Product product = new Product();
        product.setId(1L);
        product.setTitle("To Be Evicted");
        product.setStock(10);
        product.setStatus(1);
        product.setIsDeleted(0);

        cacheService.put(1L, product);
        assertThat(cacheService.getProduct(1L)).isNotNull();

        cacheService.evict(1L);
        assertThat(cacheService.getProduct(1L)).isNull();
    }

    @Test
    void shouldRefreshFromDb() {
        Product dbProduct = new Product();
        dbProduct.setId(1L);
        dbProduct.setTitle("Fresh Product");
        dbProduct.setStock(50);
        dbProduct.setStatus(1);
        dbProduct.setIsDeleted(0);

        when(productRepo.findById(1L)).thenReturn(Optional.of(dbProduct));

        cacheService.refresh(1L);
        Product result = cacheService.getProduct(1L);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Fresh Product");
        verify(productRepo, times(1)).findById(1L);
    }
}