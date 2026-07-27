-- ===================================================================
-- LiveMall 商品模块 — 16 表分片 DDL
-- 数据库：livemall_product_1
-- 分片策略：HASH_MOD(id, 16)
-- 主键：雪花 ID（由 ShardingSphere 自动生成）
-- ===================================================================

CREATE DATABASE IF NOT EXISTS livemall_product_1 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_general_ci;

USE livemall_product_1;

-- ===================================================================
-- 分表 0-15（结构完全相同）
-- ===================================================================

-- t_product_0
CREATE TABLE IF NOT EXISTS t_product_0 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 0';

-- t_product_1
CREATE TABLE IF NOT EXISTS t_product_1 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 1';

-- t_product_2
CREATE TABLE IF NOT EXISTS t_product_2 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 2';

-- t_product_3
CREATE TABLE IF NOT EXISTS t_product_3 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 3';

-- t_product_4
CREATE TABLE IF NOT EXISTS t_product_4 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 4';

-- t_product_5
CREATE TABLE IF NOT EXISTS t_product_5 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 5';

-- t_product_6
CREATE TABLE IF NOT EXISTS t_product_6 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 6';

-- t_product_7
CREATE TABLE IF NOT EXISTS t_product_7 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 7';

-- t_product_8
CREATE TABLE IF NOT EXISTS t_product_8 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 8';

-- t_product_9
CREATE TABLE IF NOT EXISTS t_product_9 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 9';

-- t_product_10
CREATE TABLE IF NOT EXISTS t_product_10 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 10';

-- t_product_11
CREATE TABLE IF NOT EXISTS t_product_11 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 11';

-- t_product_12
CREATE TABLE IF NOT EXISTS t_product_12 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 12';

-- t_product_13
CREATE TABLE IF NOT EXISTS t_product_13 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 13';

-- t_product_14
CREATE TABLE IF NOT EXISTS t_product_14 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 14';

-- t_product_15
CREATE TABLE IF NOT EXISTS t_product_15 (
    id              BIGINT NOT NULL COMMENT '雪花 ID，全局唯一主键',
    user_id         BIGINT NOT NULL COMMENT '发布者用户 ID（关联用户服务）',
    title           VARCHAR(200) NOT NULL COMMENT '商品标题',
    subtitle        VARCHAR(500) DEFAULT NULL COMMENT '副标题/卖点',
    main_image      VARCHAR(500) DEFAULT NULL COMMENT '主图 URL（可为空）',
    detail_images   VARCHAR(2000) DEFAULT NULL COMMENT '详情图 URL（JSON 数组，可为空）',
    price           DECIMAL(10,2) NOT NULL COMMENT '原价',
    stock           INT NOT NULL DEFAULT 0 COMMENT '总库存',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0:下架 1:上架 2:审核中',
    category_id     BIGINT DEFAULT NULL COMMENT '类目 ID（预留）',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常 1:已删除（软删除）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status, is_deleted),
    INDEX idx_status_created (status, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表 15';

-- ===================================================================
-- 完成：16 张分表已创建
-- ===================================================================
