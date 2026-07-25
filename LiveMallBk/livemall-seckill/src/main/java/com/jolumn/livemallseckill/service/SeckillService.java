package com.jolumn.livemallseckill.service;

import com.jolumn.livemallcommon.exception.BizException;
import com.jolumn.livemallseckill.entity.SeckillActivity;
import com.jolumn.livemallseckill.entity.SeckillOrder;
import com.jolumn.livemallseckill.repository.SeckillActivityRepository;
import com.jolumn.livemallseckill.repository.SeckillOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    private final SeckillActivityRepository activityRepo;
    private final SeckillOrderRepository orderRepo;
    private final StockService stockService;
    private final ActivityCacheService cacheService;
    private final ActivityBloomFilter bloomFilter;

    public SeckillService(SeckillActivityRepository activityRepo,
                          SeckillOrderRepository orderRepo,
                          StockService stockService,
                          ActivityCacheService cacheService,
                          ActivityBloomFilter bloomFilter) {
        this.activityRepo = activityRepo;
        this.orderRepo = orderRepo;
        this.stockService = stockService;
        this.cacheService = cacheService;
        this.bloomFilter = bloomFilter;
    }

    /** 创建秒杀活动（管理员） */
    @Transactional
    public SeckillActivity createActivity(SeckillActivity activity) {
        if (activity.getStartTime().isAfter(activity.getEndTime())) {
            throw new BizException(400, "开始时间不能晚于结束时间");
        }
        if (activity.getTotalStock() <= 0) {
            throw new BizException(400, "库存必须大于 0");
        }
        return activityRepo.save(activity);
    }

    /** 查询活动详情（走 DB — 管理端查询用） */
    public SeckillActivity getActivity(Long activityId) {
        return activityRepo.findById(activityId)
                .orElseThrow(() -> new BizException(404, "活动不存在"));
    }

    /** 查询活动详情（走 Caffeine 缓存 L1 — 下单降级路径用，避免直接穿透 DB） */
    public SeckillActivity getActivityCached(Long activityId) {
        SeckillActivity activity = cacheService.getActivity(activityId);
        if (activity == null) {
            throw new BizException(404, "活动不存在");
        }
        return activity;
    }

    /** 更新活动状态（0:待开始 1:进行中 2:已结束 3:已取消）。上架(→1)时初始化 Redis 库存分片 */
    @Transactional
    public void updateStatus(Long activityId, Integer status) {
        SeckillActivity activity = activityRepo.findById(activityId)
                .orElseThrow(() -> new BizException(404, "活动不存在"));
        if (status < 0 || status > 3) {
            throw new BizException(400, "状态值必须在 0~3 之间");
        }
        activity.setStatus(status);
        activityRepo.save(activity);

        if (status == 1) {
            stockService.initStock(activityId, activity.getTotalStock());
            cacheService.markInStock(activityId);
            cacheService.refresh(activityId);
            bloomFilter.add(activityId);
        }
    }

    /** 抢购下单。Bloom → Caffeine L1 → Redis Lua → Kafka */
    public String placeOrder(Long activityId, Long userId, String orderNo) {
        // 0. 布隆过滤器 — 快速拒绝无效 activityId
        if (!bloomFilter.mightContain(activityId)) {
            throw new BizException(404, "活动不存在");
        }

        // Caffeine L1 快速售罄检查
        if (cacheService.isSoldOut(activityId)) {
            throw new BizException(1009, "库存不足");
        }

        // Caffeine L1 缓存活动信息
        SeckillActivity activity = cacheService.getActivity(activityId);
        if (activity == null) {
            throw new BizException(404, "活动不存在");
        }
        if (activity.getStatus() != 1) {
            throw new BizException(400, "活动未在进行中");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            throw new BizException(400, "不在活动时间范围内");
        }

        int result = stockService.deduct(activityId, userId);
        return switch (result) {
            case 200 -> {
                log.info("抢购成功: activityId={}, userId={}, orderNo={}", activityId, userId, orderNo);
                yield "ok";
            }
            case -1 -> throw new BizException(1010, "已参与过该活动");
            case -2 -> {
                cacheService.markSoldOut(activityId);
                throw new BizException(1009, "库存不足");
            }
            case -3 -> throw new BizException(500, "活动未初始化");
            default -> throw new BizException(500, "系统繁忙");
        };
    }

    /** 创建订单（Kafka Consumer 调用） */
    @Transactional
    public SeckillOrder createOrder(SeckillActivity activity, Long userId, String orderNo) {
        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(orderNo);
        order.setActivityId(activity.getId());
        order.setUserId(userId);
        order.setProductId(activity.getProductId());
        order.setSeckillPrice(activity.getSeckillPrice());
        order.setStatus(0);
        return orderRepo.save(order);
    }

    /** 查询用户订单列表 */
    public List<SeckillOrder> getUserOrders(Long userId) {
        return orderRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** 查询单个订单 */
    public SeckillOrder getOrder(String orderNo) {
        return orderRepo.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(404, "订单不存在"));
    }

    /** 取消订单。先 DB 更新（@Version 防并发），后 Redis 回补（Lua EXISTS 幂等） */
    @Transactional
    public void cancelOrder(String orderNo, Long userId) {
        SeckillOrder order = orderRepo.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(404, "订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作");
        }
        if (order.getStatus() != 0) {
            throw new BizException(400, "订单状态不允许取消");
        }
        order.setStatus(2);
        order.setCancelledAt(LocalDateTime.now());
        orderRepo.save(order);

        stockService.refund(order.getActivityId(), userId);
        cacheService.markInStock(order.getActivityId());
    }

    /** 退款 */
    @Transactional
    public void refundOrder(String orderNo) {
        SeckillOrder order = orderRepo.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(404, "订单不存在"));
        if (order.getStatus() != 1) {
            throw new BizException(400, "订单状态不允许退款");
        }
        order.setStatus(3);
        orderRepo.save(order);
    }

    /** 查询活动列表。传 roomId → 仅该房间进行中（C 端用），不传 → 全部（管理端用） */
    public List<SeckillActivity> getActivities(Long roomId) {
        if (roomId != null) {
            return activityRepo.findByRoomIdAndStatusOrderByStartTimeAsc(roomId, 1);
        }
        return activityRepo.findAllByOrderByStartTimeDesc();
    }

    /** 查询进行中的活动 */
    public List<SeckillActivity> getActiveActivities() {
        return activityRepo.findByStatusOrderByStartTimeAsc(1);
    }
}
