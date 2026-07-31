package com.jolumn.vtslproduct.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.jolumn.vtslproduct.entity.Product;
import com.jolumn.vtslproduct.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ProductCacheService {

    private final LoadingCache<Long, Product> productCache;
    private final ProductRepository productRepo;

    public ProductCacheService(ProductRepository productRepo,
                               @Value("${product.cache.ttl-seconds:5}") int ttlSeconds,
                               @Value("${product.cache.max-size:2000}") int maxSize) {
        this.productRepo = productRepo;
        this.productCache = Caffeine.newBuilder()
                .refreshAfterWrite(Duration.ofSeconds(ttlSeconds))
                .expireAfterAccess(Duration.ofSeconds(30))
                .maximumSize(maxSize)
                .build(id -> productRepo.findById(id).orElse(null));
    }

    public Product getProduct(Long productId) {
        return productCache.get(productId);
    }

    public void put(Long productId, Product product) {
        productCache.put(productId, product);
    }

    public void evict(Long productId) {
        productCache.invalidate(productId);
    }

    public void refresh(Long productId) {
        productRepo.findById(productId).ifPresent(p -> productCache.put(productId, p));
    }
}
