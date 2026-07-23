package com.jolumn.livemallseckill.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 创建秒杀活动请求 DTO — 接收前端字段名，转换为 SeckillActivity 实体。
 * 前端字段名：name/price/origPrice/stockTotal/startAt/endAt（epoch ms）
 */
public class CreateActivityRequest {

    @NotBlank(message = "活动名称不能为空")
    private String name;            // 前端 name → 实体 title

    @NotNull(message = "秒杀价不能为空")
    @Min(value = 1, message = "秒杀价必须大于 0")
    private BigDecimal price;       // 前端 price → 实体 seckillPrice

    @NotNull(message = "原价不能为空")
    @Min(value = 1, message = "原价必须大于 0")
    private BigDecimal origPrice;   // 前端 origPrice → 实体 originalPrice

    @NotNull(message = "库存不能为空")
    @Min(value = 1, message = "库存必须大于 0")
    private Integer stockTotal;     // 前端 stockTotal → 实体 totalStock

    @NotNull(message = "开始时间不能为空")
    private Long startAt;           // 前端 startAt (epoch ms) → 实体 startTime (LocalDateTime)

    @NotNull(message = "结束时间不能为空")
    private Long endAt;             // 前端 endAt (epoch ms) → 实体 endTime (LocalDateTime)

    private Long productId;         // 商品 ID（可选）
    private Long roomId;            // 关联直播间（可选）

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getOrigPrice() { return origPrice; }
    public void setOrigPrice(BigDecimal origPrice) { this.origPrice = origPrice; }
    public Integer getStockTotal() { return stockTotal; }
    public void setStockTotal(Integer stockTotal) { this.stockTotal = stockTotal; }
    public Long getStartAt() { return startAt; }
    public void setStartAt(Long startAt) { this.startAt = startAt; }
    public Long getEndAt() { return endAt; }
    public void setEndAt(Long endAt) { this.endAt = endAt; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }

    /** 转换为 SeckillActivity 实体 */
    public com.jolumn.livemallseckill.entity.SeckillActivity toEntity() {
        com.jolumn.livemallseckill.entity.SeckillActivity entity =
                new com.jolumn.livemallseckill.entity.SeckillActivity();
        entity.setTitle(this.name);
        entity.setSeckillPrice(this.price);
        entity.setOriginalPrice(this.origPrice);
        entity.setTotalStock(this.stockTotal);
        entity.setStartTime(toLocalDateTime(this.startAt));
        entity.setEndTime(toLocalDateTime(this.endAt));
        entity.setStatus(0); // 待开始
        if (this.productId != null) {
            entity.setProductId(this.productId);
        }
        if (this.roomId != null) {
            entity.setRoomId(this.roomId);
        }
        return entity;
    }

    private static LocalDateTime toLocalDateTime(Long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }
}
