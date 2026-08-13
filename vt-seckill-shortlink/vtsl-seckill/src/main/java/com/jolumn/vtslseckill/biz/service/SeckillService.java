package com.jolumn.vtslseckill.biz.service;

import com.jolumn.vtslcommon.exception.BizException;
import com.jolumn.vtslseckill.model.entity.SeckillActivity;
import com.jolumn.vtslseckill.model.entity.SeckillOrder;
import com.jolumn.vtslseckill.model.enums.BizErrorCode;
import com.jolumn.vtslseckill.model.enums.SeckillMode;
import com.jolumn.vtslseckill.biz.service.strategy.SeckillStrategy;
import com.jolumn.vtslseckill.biz.service.strategy.SeckillStrategyFactory;
import com.jolumn.vtslseckill.biz.repository.SeckillActivityRepository;
import com.jolumn.vtslseckill.biz.repository.SeckillOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    private final SeckillActivityRepository activityRepo;
    private final SeckillOrderRepository orderRepo;
    private final StockService stockService;
    private final ActivityCacheService cacheService;
    private final SeckillStrategyFactory seckillStrategyFactory;

    public SeckillService(SeckillActivityRepository activityRepo,
                          SeckillOrderRepository orderRepo,
                          StockService stockService,
                          ActivityCacheService cacheService,
                          SeckillStrategyFactory factory) {
        this.activityRepo = activityRepo;
        this.orderRepo = orderRepo;
        this.stockService = stockService;
        this.cacheService = cacheService;
        this.seckillStrategyFactory = factory;
    }

    /**
     * 创建秒杀活动
     * @param activity
     * @return
     */
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

    /**
     * 获取活动信息 - 直查DB
     * @param activityId
     * @return
     */
    public SeckillActivity getActivity(Long activityId) {
        return activityRepo.findById(activityId)
                .orElseThrow(() -> new BizException(404, "活动不存在"));
    }

    /**
     * 从缓存查活动信息
     * @param activityId
     * @return
     */
    public SeckillActivity getActivityCached(Long activityId) {
        SeckillActivity activity = cacheService.getActivity(activityId);
        if (activity == null) {
            throw new BizException(404, "活动不存在");
        }
        return activity;
    }

    /**
     * 更新活动状态
     * @param activityId
     * @param status
     */
    @Transactional
    public void updateStatus(Long activityId, Integer status) {
        SeckillActivity activity = activityRepo.findById(activityId)
                .orElseThrow(() -> new BizException(404, "活动不存在"));
        if (status < 0 || status > 3) {
            throw new BizException(400, "状态值必须在 0~3 之间");
        }
        activity.setStatus(status);
        activityRepo.save(activity);

        if (status == 1 && activity.getMode() != SeckillMode.DB_QUEUE) {
            stockService.initStock(activityId, activity.getTotalStock());
            cacheService.refresh(activityId);
        }
    }

    /**
     * 扣库存
     * @param activityId
     * @param userId
     * @param orderNo
     * @return
     */
    public String placeOrder(Long activityId, Long userId, String orderNo) {
        SeckillActivity activity = cacheService.getActivity(activityId);
        if (activity == null)
            return BizErrorCode.ACTIVITY_NOT_EXIST.getMsg();

        if (activity.getStatus() != 1)
            return BizErrorCode.ACTIVITY_NOT_STARTED.getMsg();

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime()))
            return BizErrorCode.ACTIVITY_NOT_IN_TIME.getMsg();


        // 工厂模式选择 扣库存+落库 策略
        SeckillStrategy strategy = seckillStrategyFactory.getStrategy(activity.getMode());

        int result;
        try {
            result = strategy.deductStock(activityId, userId);
        } catch (RuntimeException e) {
            throw new BizException(503, "系统繁忙，请稍后重试");
        }

        if (result == 200) {
            try {
                strategy.createOrder(activity, userId, orderNo);
//                log.info("抢购成功: activityId={}, userId={}, orderNo={}, mode={}",
//                        activityId, userId, orderNo, activity.getMode());
                return "ok";
            } catch (Exception e) {
//                log.error("创建订单失败, 回补库存: activityId={}, userId={}, mode={}",
//                        activityId, userId, activity.getMode(), e);
                strategy.refundStock(activityId, userId);
                throw new BizException(503, "系统繁忙，请稍后重试");
            }
        }

        return switch (result) {
            case -1 -> BizErrorCode.USER_REPEAT_ORDER.getMsg();
            case -2 -> BizErrorCode.STOCK_NOT_ENOUGH.getMsg();
            case -3 -> BizErrorCode.ACTIVITY_NOT_INIT.getMsg();
            default -> BizErrorCode.SYSTEM_BUSY.getMsg();
        };
    }


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

    public List<SeckillOrder> getUserOrders(Long userId) {
        return orderRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public SeckillOrder getOrder(String orderNo) {
        return orderRepo.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(404, "订单不存在"));
    }

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

        SeckillActivity activity = activityRepo.findById(order.getActivityId())
                .orElseThrow(() -> new BizException(404, "活动不存在"));
        SeckillStrategy strategy = seckillStrategyFactory.getStrategy(activity.getMode());
        strategy.refundStock(order.getActivityId(), userId);
    }

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

    public List<SeckillActivity> getActivities(Long roomId) {
        if (roomId != null) {
            return activityRepo.findByRoomIdAndStatusOrderByStartTimeAsc(roomId, 1);
        }
        return activityRepo.findAllByOrderByStartTimeDesc();
    }

    public List<SeckillActivity> getActiveActivities() {
        return activityRepo.findByStatusOrderByStartTimeAsc(1);
    }
}
