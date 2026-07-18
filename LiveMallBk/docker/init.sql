-- ============================================================
-- LiveMall 数据库初始化
-- 执行方式: docker exec -i livemall-mysql mysql -uroot -proot123 livemall < init.sql
-- ============================================================

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    nickname   VARCHAR(50),
    phone      VARCHAR(20),
    avatar     VARCHAR(255),
    role       TINYINT      DEFAULT 1 COMMENT '1=观众 2=主播 3=管理员',
    status     TINYINT      DEFAULT 1 COMMENT '0=封禁 1=正常',
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 直播间表
CREATE TABLE IF NOT EXISTS t_live_room (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(80)  NOT NULL COMMENT '直播标题',
    anchor_id   BIGINT       NOT NULL COMMENT '主播用户ID',
    anchor_name VARCHAR(50)  NOT NULL COMMENT '主播名称',
    category    VARCHAR(20)  DEFAULT 'other' COMMENT 'digital/beauty/food/fashion/other',
    cover_color VARCHAR(100) COMMENT '封面渐变色',
    status      TINYINT      DEFAULT 0 COMMENT '0=离线 1=直播中',
    online_count INT         DEFAULT 0 COMMENT '在线人数',
    started_at  DATETIME     COMMENT '开播时间',
    ended_at    DATETIME     COMMENT '关播时间',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_anchor (anchor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播间';
