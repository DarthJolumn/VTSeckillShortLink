package com.jolumn.livemallseckill.product.service;

import com.jolumn.livemallcommon.api.ProductShortLinkService;
import com.jolumn.livemallseckill.product.entity.Product;
import com.jolumn.livemallseckill.product.repository.ProductRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 商品短链 RPC 服务实现
 * <p>
 * 供短链服务调用，用于算法推导后的商品状态校验
 */
@DubboService
public class ProductShortLinkServiceImpl implements ProductShortLinkService {

    private static final Logger log = LoggerFactory.getLogger(ProductShortLinkServiceImpl.class);
    private static final String PRODUCT_URL_TEMPLATE = "https://www.livemall.com/product/%d";

    private final ProductRepository productRepository;

    public ProductShortLinkServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public String getAvailableProductUrl(Long productId) {
        if (!isProductAvailable(productId)) {
            return null;
        }
        return String.format(PRODUCT_URL_TEMPLATE, productId);
    }

    @Override
    public boolean isProductAvailable(Long productId) {
        return productRepository.findAvailableById(productId).isPresent();
    }
}
