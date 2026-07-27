package com.jolumn.vtslcommon.api;

/**
 * 商品短链 RPC 接口
 * <p>
 * 供短链服务调用，用于算法推导后的商品状态校验和 URL 获取
 * <p>
 * 使用场景：
 * 1. 短链服务收到短码 "P5Fg8Kp2mN9"
 * 2. 算法解码得到 productId
 * 3. 调用此接口校验商品是否可售
 * 4. 返回商品详情页 URL 用于 302 跳转
 */
public interface ProductShortLinkService {

    /**
     * 校验商品是否可售并返回详情页 URL
     *
     * @param productId 商品 ID（从短码解码得到）
     * @return 商品详情页 URL，如果商品不可售返回 null
     */
    String getAvailableProductUrl(Long productId);

    /**
     * 检查商品是否可售（不返回 URL）
     *
     * @param productId 商品 ID
     * @return true=可售，false=不可售（下架/删除/库存不足）
     */
    boolean isProductAvailable(Long productId);
}
