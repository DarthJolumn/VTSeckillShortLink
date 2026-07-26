package com.jolumn.livemallseckill.product.service;

import com.jolumn.livemallcommon.dto.PageResult;
import com.jolumn.livemallseckill.product.dto.ProductDTO;
import com.jolumn.livemallseckill.product.dto.ProductPublishCmd;
import com.jolumn.livemallseckill.product.dto.ProductUpdateCmd;

public interface ProductFacade {

    Long publish(ProductPublishCmd cmd);

    ProductDTO getById(Long productId);

    boolean checkAndDecrStock(Long productId, int quantity);

    boolean checkAvailable(Long productId);

    PageResult<ProductDTO> list(int page, int size, Long userId, Long categoryId, String sortBy, String sortDir);

    void update(Long id, Long userId, ProductUpdateCmd cmd);

    void updateStatus(Long id, Long userId, Integer status);

    void softDelete(Long id, Long userId);
}
