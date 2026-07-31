package com.jolumn.vtslcommon.api;

public interface ProductRpcService {

    boolean isProductAvailable(Long productId);

    String getAvailableProductUrl(Long productId);

    boolean checkAndDecrStock(Long productId, int quantity);

    void incrementStock(Long productId, int quantity);
}
