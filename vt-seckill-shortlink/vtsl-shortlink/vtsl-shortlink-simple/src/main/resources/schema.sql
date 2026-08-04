-- ===================================================================
-- 短链系统 — 数据库表结构
-- ===================================================================

USE livemall;

-- t_short_link — 短链映射表
DROP TABLE IF EXISTS t_short_link;
CREATE TABLE t_short_link (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    short_code      VARCHAR(20) NOT NULL UNIQUE COMMENT '短码（Base62 编码）',
    user_id         BIGINT COMMENT '创建人 ID',
    title           VARCHAR(200) COMMENT '标题（可选）',
    product_id      BIGINT NOT NULL COMMENT '关联商品 ID',
    original_url    VARCHAR(2000) NOT NULL COMMENT '原始长链（商品详情页 URL）',
    url_hash        CHAR(32) NOT NULL COMMENT 'MD5(original_url)，用于去重',
    click_count     BIGINT DEFAULT 0 COMMENT '点击次数',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    expire_at       DATETIME NOT NULL COMMENT '过期时间',
    status          TINYINT DEFAULT 1 COMMENT '1:正常 2:已删除',
    UNIQUE INDEX uk_short_code (short_code),
    INDEX idx_url_hash (url_hash),
    INDEX idx_product_id (product_id),
    INDEX idx_user_id (user_id),
    INDEX idx_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短链映射表';

-- t_link_click_stats — 点击统计表（按天分区）
DROP TABLE IF EXISTS t_link_click_stats;
CREATE TABLE t_link_click_stats (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    short_code      VARCHAR(20) NOT NULL COMMENT '短码',
    click_date      DATE NOT NULL COMMENT '点击日期',
    click_count     INT DEFAULT 0 COMMENT '当日点击次数',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_code_date (short_code, click_date),
    INDEX idx_click_date (click_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点击统计表（按天）';
