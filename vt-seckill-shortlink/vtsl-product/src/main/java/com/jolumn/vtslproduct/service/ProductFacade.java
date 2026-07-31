package com.jolumn.vtslproduct.service;

import com.jolumn.vtslcommon.dto.PageResult;
import com.jolumn.vtslproduct.dto.ProductDTO;
import com.jolumn.vtslproduct.dto.ProductPublishCmd;
import com.jolumn.vtslproduct.dto.ProductUpdateCmd;

public interface ProductFacade {

    Long publish(Long userId, ProductPublishCmd cmd);

    ProductDTO getById(Long productId);

    boolean checkAndDecrStock(Long productId, int quantity);

    boolean checkAvailable(Long productId);

    PageResult<ProductDTO> list(int page, int size, Long userId, Long categoryId, String sortBy, String sortDir);

    void update(Long id, Long userId, ProductUpdateCmd cmd);

    void updateStatus(Long id, Long userId, Integer status);

    void softDelete(Long id, Long userId);
}
