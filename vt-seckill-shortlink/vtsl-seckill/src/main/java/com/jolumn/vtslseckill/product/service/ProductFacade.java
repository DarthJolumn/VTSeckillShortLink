package com.jolumn.vtslseckill.product.service;

import com.jolumn.vtslcommon.dto.PageResult;
import com.jolumn.vtslseckill.product.dto.ProductDTO;
import com.jolumn.vtslseckill.product.dto.ProductPublishCmd;
import com.jolumn.vtslseckill.product.dto.ProductUpdateCmd;

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
