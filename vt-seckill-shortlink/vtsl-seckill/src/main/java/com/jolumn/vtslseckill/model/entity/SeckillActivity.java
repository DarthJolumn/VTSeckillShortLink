package com.jolumn.vtslseckill.model.entity;

import com.jolumn.vtslseckill.model.enums.SeckillMode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_seckill_activity")
public class SeckillActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "seckill_mode", nullable = false, length = 20)
    private SeckillMode mode = SeckillMode.REDIS_ASYNC;

    @Version
    @Column(name = "version")
    private Integer version;


    @Column(name = "seckill_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal seckillPrice;

    @Column(name = "original_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "total_stock", nullable = false)
    private Integer totalStock;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private Integer status;  // 0:待开始 1:进行中 2:已结束 3:已取消

    @Column(name = "room_id")
    private Long roomId;     // 关联直播间

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = 0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public BigDecimal getSeckillPrice() { return seckillPrice; }
    public void setSeckillPrice(BigDecimal price) { this.seckillPrice = price; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal price) { this.originalPrice = price; }
    public Integer getTotalStock() { return totalStock; }
    public void setTotalStock(Integer stock) { this.totalStock = stock; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime time) { this.startTime = time; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime time) { this.endTime = time; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setMode(SeckillMode mode) {this.mode = mode;}
    public SeckillMode getMode() {return mode;}
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
