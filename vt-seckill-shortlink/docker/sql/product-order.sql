-- ===================================================================
-- LiveMall 商品模块 — 订单表（建在 livemall_product_1 库）
-- ===================================================================

USE livemall_product_1;

DROP TABLE IF EXISTS t_product_order;
CREATE TABLE t_product_order (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no        VARCHAR(50) NOT NULL UNIQUE COMMENT 'Snowflake 订单号',
    product_id      BIGINT NOT NULL COMMENT '商品 ID',
    user_id         BIGINT NOT NULL COMMENT '买家用户 ID',
    quantity        INT NOT NULL DEFAULT 1 COMMENT '购买数量',
    price           DECIMAL(10,2) NOT NULL COMMENT '成交单价（下单时快照）',
    total_amount    DECIMAL(10,2) NOT NULL COMMENT '总金额 = price × quantity',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:待支付 1:已支付 2:已取消 3:已退款',
    paid_at         DATETIME COMMENT '支付时间',
    cancelled_at    DATETIME COMMENT '取消时间',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT DEFAULT 0 COMMENT '乐观锁',
    INDEX idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品订单表';
