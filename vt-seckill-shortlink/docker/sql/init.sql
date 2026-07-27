-- ===================================================================
-- LiveMall — 数据库初始化脚本
-- 数据库：livemall
-- 字符集：utf8mb4
-- ===================================================================

CREATE DATABASE IF NOT EXISTS livemall
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE livemall;

-- ===================================================================
-- 用户域
-- ===================================================================

-- t_user — 用户表（详见 2.7 §1.1）
-- 注：t_device_session 已废弃（P0-2 修复，设备会话改 Redis 维护）
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,                          -- BCrypt hash
    nickname    VARCHAR(50),                                   -- 昵称
    phone       VARCHAR(20),                                   -- 可选
    avatar      VARCHAR(255),
    role        TINYINT DEFAULT 1 COMMENT '1:观众 2:主播 3:管理员',
    status      TINYINT DEFAULT 1 COMMENT '0:封禁 1:正常',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ===================================================================
-- 直播域
-- ===================================================================

-- t_live_room — 直播间表（详见 2.7 §2.1）
DROP TABLE IF EXISTS t_live_room;
CREATE TABLE t_live_room (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    anchor_id   BIGINT NOT NULL,
    title       VARCHAR(200),
    status      TINYINT DEFAULT 0 COMMENT '0:离线 1:直播中',
    start_time  DATETIME,
    end_time    DATETIME,
    max_viewers INT DEFAULT 10000,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_anchor_status (anchor_id, status),
    INDEX idx_status_start (status, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播间表';

-- t_barrage — 弹幕记录表（详见 2.7 §2.2）
DROP TABLE IF EXISTS t_barrage;
CREATE TABLE t_barrage (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id     BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    content     VARCHAR(500) NOT NULL COMMENT '已过滤后的弹幕内容',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room_created (room_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='弹幕记录表';

-- t_gift_log — 礼物记录表（详见 2.7 §2.3）
DROP TABLE IF EXISTS t_gift_log;
CREATE TABLE t_gift_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id     BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    gift_id     INT NOT NULL,
    gift_name   VARCHAR(50),
    gift_price  DECIMAL(10,2) NOT NULL,
    quantity    INT DEFAULT 1,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room_created (room_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='礼物记录表';

-- ===================================================================
-- 秒杀域
-- ===================================================================

-- t_seckill_activity — 秒杀活动表（详见 2.7 §3.1）
DROP TABLE IF EXISTS t_seckill_activity;
CREATE TABLE t_seckill_activity (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    title           VARCHAR(200) NOT NULL,
    product_id      BIGINT NOT NULL,
    seckill_price   DECIMAL(10,2) NOT NULL,
    original_price  DECIMAL(10,2) NOT NULL,
    total_stock     INT NOT NULL,
    start_time      DATETIME NOT NULL,
    end_time        DATETIME NOT NULL,
    status          TINYINT DEFAULT 0 COMMENT '0:待开始 1:进行中 2:已结束 3:已取消',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_title_product (title, product_id),
    INDEX idx_status_time (status, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动表';

-- t_seckill_order — 秒杀订单表（详见 2.7 §3.2）
-- 设计：id 自增做聚簇 PK；order_no（Snowflake）做业务 PK，二级唯一索引
DROP TABLE IF EXISTS t_seckill_order;
CREATE TABLE t_seckill_order (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no        VARCHAR(50) NOT NULL UNIQUE COMMENT 'Snowflake 订单号',
    activity_id     BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    product_id      BIGINT NOT NULL,
    seckill_price   DECIMAL(10,2) NOT NULL,
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:待支付 1:已支付 2:已取消 3:已退款',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    paid_at         DATETIME,
    cancelled_at    DATETIME,
    version         INT DEFAULT 0 COMMENT '乐观锁版本号',
    UNIQUE INDEX uk_activity_user (activity_id, user_id),         -- 一人一单
    INDEX idx_status_created (status, created_at)                -- 超时取消扫描
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

-- ===================================================================
-- 排行域
-- ===================================================================

-- t_leaderboard_snapshot — 排行快照表（详见 2.7 §4.1）
DROP TABLE IF EXISTS t_leaderboard_snapshot;
CREATE TABLE t_leaderboard_snapshot (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id     BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    score           DECIMAL(15,2) NOT NULL,
    rank            INT NOT NULL,
    snapshot_time   DATETIME NOT NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_activity_time (activity_id, snapshot_time),
    INDEX idx_user_activity (user_id, activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排行快照表';

-- ===================================================================
-- 初始化数据
-- ===================================================================

-- 默认管理员账号（密码：admin123，BCrypt hash）
-- BCrypt.hashpw("admin123", BCrypt.gensalt(10)) 的结果
INSERT INTO t_user (username, password, phone, role, status)
VALUES (
    'admin',
    '$2a$10$N9qo8uLOickgx2ZMRZoMye.IjvCpQRp7sZKX7Qi3vGZcJ0ZJUfYKm',
    '13800000000',
    3,
    1
);

-- 测试主播账号（密码：anchor123）
INSERT INTO t_user (username, password, phone, role, status)
VALUES (
    'anchor_test',
    '$2a$10$EqXmCnVlLmFkQ4o6rJzJr.Ll7Bl8GyYj3ez3tYk0lz8m5y3w3e8Oi',
    '13800000001',
    2,
    1
);

-- 测试观众账号（密码：user123）
INSERT INTO t_user (username, password, phone, role, status)
VALUES (
    'user_test',
    '$2a$10$5vK3sFkVj8nZ8cX8y7p9z.Wq5p1nMPD1d3BCQZqL7g3mX8uLZ8Km',
    '13800000002',
    1,
    1
);
