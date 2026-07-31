package com.jolumn.vtslproduct.service;

import com.jolumn.vtslcommon.api.ProductRpcService;
import com.jolumn.vtslcommon.api.ProductShortLinkService;
import com.jolumn.vtslproduct.repository.ProductRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

@DubboService
public class ProductRpcServiceImpl implements ProductRpcService, ProductShortLinkService {

    private static final Logger log = LoggerFactory.getLogger(ProductRpcServiceImpl.class);
    private static final String PRODUCT_URL_TEMPLATE = "/product/%d";

    private final ProductRepository productRepository;

    public ProductRpcServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public boolean isProductAvailable(Long productId) {
        return productRepository.findAvailableById(productId).isPresent();
    }

    @Override
    public String getAvailableProductUrl(Long productId) {
        if (!isProductAvailable(productId)) {
            return null;
        }
        return String.format(PRODUCT_URL_TEMPLATE, productId);
    }

    @Override
    @Transactional
    public boolean checkAndDecrStock(Long productId, int quantity) {
        int updated = productRepository.decrementStock(productId, quantity);
        if (updated > 0) {
            log.info("RPC 库存扣减成功: productId={}, quantity={}", productId, quantity);
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public void incrementStock(Long productId, int quantity) {
        productRepository.incrementStock(productId, quantity);
        log.info("RPC 库存回补成功: productId={}, quantity={}", productId, quantity);
    }
}
