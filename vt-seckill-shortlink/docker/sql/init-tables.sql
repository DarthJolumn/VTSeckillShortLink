-- LiveMall 建表脚本（在 livemall 库执行）
-- 当前只有 t_user 和 t_live_room，补上秒杀和排行榜表

-- 秒杀活动表
CREATE TABLE IF NOT EXISTS t_seckill_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    product_id BIGINT NOT NULL,
    seckill_price DECIMAL(10,2) NOT NULL,
    original_price DECIMAL(10,2) NOT NULL,
    total_stock INT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status TINYINT DEFAULT 0,
    room_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 秒杀订单表
CREATE TABLE IF NOT EXISTS t_seckill_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    seckill_price DECIMAL(10,2) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    paid_at DATETIME,
    cancelled_at DATETIME,
    version INT DEFAULT 0,
    UNIQUE INDEX uk_activity_user (activity_id, user_id),
    INDEX idx_status_created (status, created_at)
);

-- 排行榜快照表
CREATE TABLE IF NOT EXISTS t_leaderboard_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score DECIMAL(15,2) NOT NULL,
    `rank` INT NOT NULL,
    snapshot_time DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_activity_time (activity_id, snapshot_time)
);

-- 弹幕表
CREATE TABLE IF NOT EXISTS t_barrage (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room_created (room_id, created_at)
);

-- 礼物记录表
CREATE TABLE IF NOT EXISTS t_gift_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    gift_id INT NOT NULL,
    gift_name VARCHAR(50),
    gift_price DECIMAL(10,2) NOT NULL,
    quantity INT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room_created (room_id, created_at)
);

-- User 表加 balance 列
-- MySQL 8 不支持 IF NOT EXISTS, 忽略重复列错误
ALTER TABLE t_user ADD COLUMN balance DECIMAL(15,2) DEFAULT 8888.00;
