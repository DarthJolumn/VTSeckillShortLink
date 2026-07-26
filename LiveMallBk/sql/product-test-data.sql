-- ===================================================================
-- LiveMall 商品订单测试数据
-- 目标库: livemall_product_1
-- ===================================================================

USE livemall_product_1;

-- ===== 1. 先插入测试商品（如已有可跳过） =====
INSERT IGNORE INTO t_product_0 (id, user_id, title, subtitle, main_image, detail_images, price, stock, status, category_id, is_deleted, created_at, updated_at) VALUES
(202607010001001, 1001, '旗舰降噪耳机 Pro', 'ANC 自适应降噪，40dB 深度', '/img/headphone.jpg', '["/img/hp1.jpg","/img/hp2.jpg"]', 899.00, 1000, 1, 1, 0, NOW(), NOW()),
(202607010001002, 1001, '真无线蓝牙耳机 Air', '14.2mm 动圈，30h 续航', '/img/earbuds.jpg', '["/img/eb1.jpg","/img/eb2.jpg"]', 299.00, 2000, 1, 1, 0, NOW(), NOW()),
(202607010001003, 1002, '机械键盘 K87', '热插拔轴体，RGB 背光', '/img/keyboard.jpg', '["/img/kb1.jpg","/img/kb2.jpg"]', 459.00, 500, 1, 2, 0, NOW(), NOW()),
(202607010001004, 1002, '电竞鼠标 M3', 'PAW3395 传感器，65g 轻量化', '/img/mouse.jpg', '["/img/ms1.jpg","/img/ms2.jpg"]', 259.00, 800, 1, 2, 0, NOW(), NOW()),
(202607010001005, 1003, '有机绿茶礼盒 500g', '明前采摘，高山云雾', '/img/tea.jpg', '["/img/tea1.jpg","/img/tea2.jpg"]', 168.00, 300, 1, 3, 0, NOW(), NOW()),
(202607010001006, 1003, '即溶咖啡 100 条装', '阿拉比卡豆，冷热双泡', '/img/coffee.jpg', '["/img/cof1.jpg","/img/cof2.jpg"]', 128.00, 1500, 1, 3, 0, NOW(), NOW()),
(202607010001007, 1001, '智能手环 S1', '血氧/心率/睡眠监测', '/img/band.jpg', '["/img/bd1.jpg","/img/bd2.jpg"]', 199.00, 600, 0, 1, 0, NOW(), NOW()),
(202607010001008, 1004, '国风真丝围巾', '100% 桑蚕丝，手工卷边', '/img/scarf.jpg', '["/img/sc1.jpg","/img/sc2.jpg"]', 399.00, 200, 1, 4, 0, NOW(), NOW());

-- ===== 2. 批量订单（用户 1001~1008 × 商品 1~6，覆盖各状态） =====
INSERT INTO t_product_order (order_no, product_id, user_id, quantity, price, total_amount, status, paid_at, cancelled_at, created_at, updated_at) VALUES

-- 用户 1001 的订单（3 笔：待支付、已支付、已取消）
('202607260001001', 202607010001001, 1001, 1, 899.00, 899.00, 0, NULL, NULL, DATE_SUB(NOW(), INTERVAL 5 MINUTE), NOW()),
('202607260001002', 202607010001002, 1001, 2, 299.00, 598.00, 1, DATE_SUB(NOW(), INTERVAL 10 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 30 MINUTE), NOW()),
('202607260001003', 202607010001003, 1001, 1, 459.00, 459.00, 2, NULL, DATE_SUB(NOW(), INTERVAL 2 MINUTE), DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW()),

-- 用户 1002 的订单（3 笔）
('202607260002001', 202607010001004, 1002, 1, 259.00, 259.00, 0, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 MINUTE), NOW()),
('202607260002002', 202607010001005, 1002, 2, 168.00, 336.00, 1, DATE_SUB(NOW(), INTERVAL 20 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW()),
('202607260002003', 202607010001006, 1002, 3, 128.00, 384.00, 3, DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW(), DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW()),

-- 用户 1003 的订单（2 笔）
('202607260003001', 202607010001001, 1003, 1, 899.00, 899.00, 1, DATE_SUB(NOW(), INTERVAL 5 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 15 MINUTE), NOW()),
('202607260003002', 202607010001006, 1003, 5, 128.00, 640.00, 0, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 MINUTE), NOW()),

-- 模拟首页展示的 8 条商品订单（limit 8 场景）
('202607260004001', 202607010001002, 1004, 1, 299.00, 299.00, 1, DATE_SUB(NOW(), INTERVAL 2 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 10 MINUTE), NOW()),
('202607260004002', 202607010001005, 1005, 2, 168.00, 336.00, 1, DATE_SUB(NOW(), INTERVAL 8 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 20 MINUTE), NOW()),
('202607260004003', 202607010001003, 1006, 1, 459.00, 459.00, 0, NULL, NULL, DATE_SUB(NOW(), INTERVAL 30 SECOND), NOW()),
('202607260004004', 202607010001004, 1007, 1, 259.00, 259.00, 2, NULL, DATE_SUB(NOW(), INTERVAL 1 MINUTE), DATE_SUB(NOW(), INTERVAL 5 MINUTE), NOW()),
('202607260004005', 202607010001006, 1008, 10, 128.00, 1280.00, 1, DATE_SUB(NOW(), INTERVAL 30 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW());

-- ===== 3. 秒杀订单（livemall 库，用于秒杀模块测试） =====
-- USE livemall;
-- INSERT INTO t_seckill_order (order_no, activity_id, product_id, user_id, price, quantity, total_amount, status, created_at)
-- VALUES ...;
