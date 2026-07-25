package com.jolumn.livemallseckill.controller;

import com.jolumn.livemallcommon.dto.Result;
import com.jolumn.livemallcommon.exception.BizException;
import com.jolumn.livemallcommon.util.SnowflakeIdGenerator;
import com.jolumn.livemallseckill.dto.CreateActivityRequest;
import com.jolumn.livemallseckill.entity.SeckillActivity;
import com.jolumn.livemallseckill.entity.SeckillOrder;
import com.jolumn.livemallseckill.service.SeckillService;
import com.jolumn.livemallseckill.service.StockService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/seckill")
public class SeckillController {

    private static final Logger log = LoggerFactory.getLogger(SeckillController.class);

    private final SeckillService seckillService;
    private final SnowflakeIdGenerator idGenerator;
    @Autowired private StockService stockService;
    @Autowired(required = false) private KafkaTemplate<String, String> kafkaTemplate;

    public SeckillController(SeckillService seckillService,
                             SnowflakeIdGenerator idGenerator) {
        this.seckillService = seckillService;
        this.idGenerator = idGenerator;
    }

    /** 创建秒杀活动（管理员）— 前端字段名 name/price/origPrice/stockTotal/startAt/endAt(ms) */
    @PostMapping("/activity")
    public Result<SeckillActivity> createActivity(@Valid @RequestBody CreateActivityRequest request) {
        return Result.ok(seckillService.createActivity(request.toEntity()));
    }

    /** 更新活动状态（上架 1 / 下架 2） */
    @PutMapping("/activity/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        seckillService.updateStatus(id, body.get("status"));
        return Result.ok();
    }

    /** 活动详情 */
    @GetMapping("/activity/{id}")
    public Result<SeckillActivity> activityDetail(@PathVariable Long id) {
        return Result.ok(seckillService.getActivity(id));
    }

    /** 活动列表（可选 roomId 过滤） */
    @GetMapping("/activity/list")
    public Result<List<SeckillActivity>> activityList(@RequestParam(required = false) Long roomId) {
        return Result.ok(seckillService.getActivities(roomId));
    }

    /**
     * 抢购下单 → 返回 {result, orderNo} 供前端匹配 WS SEC_KILL_RESULT
     * Kafka 不可用时 Fail Fast 返回 503 + 回补库存
     */
    @PostMapping("/order")
    public Result<java.util.Map<String, Object>> placeOrder(@RequestBody java.util.Map<String, Long> body,
                                     @RequestHeader("X-User-Id") Long userId) {
        Long activityId = body.get("activityId");
        String orderNo = idGenerator.nextOrderNo();

        String result = seckillService.placeOrder(activityId, userId, orderNo);

        // Lua 扣减成功 → 发 Kafka（异步）
        if ("ok".equals(result)) {
            if (kafkaTemplate != null) {
                String msg = userId + ":" + activityId + ":" + orderNo;
                try {
                    kafkaTemplate.send("seckill-order", msg).get(3, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Fail Fast: Kafka 不可用 → 回补库存 → 返回 503
                    log.error("Kafka 不可用, Fail Fast 回补库存: activityId={}, userId={}", activityId, userId);
                    stockService.refund(activityId, userId);
                    throw new BizException(503, "系统繁忙，请稍后重试");
                }
            } else {
                // 无 Kafka bean（开发环境）→ Fail Fast 返回 503 + 回补库存
                log.error("Kafka 未配置, Fail Fast 回补库存: activityId={}, userId={}", activityId, userId);
                stockService.refund(activityId, userId);
                throw new BizException(503, "系统繁忙，请稍后重试");
            }
        }
        return Result.ok(java.util.Map.of("result", result, "orderNo", orderNo));
    }

    /** 订单列表 */
    @GetMapping("/order/list")
    public Result<List<SeckillOrder>> orderList(@RequestHeader("X-User-Id") Long userId) {
        return Result.ok(seckillService.getUserOrders(userId));
    }

    /** 订单详情 */
    @GetMapping("/order/{orderNo}")
    public Result<SeckillOrder> orderDetail(@PathVariable String orderNo) {
        return Result.ok(seckillService.getOrder(orderNo));
    }

    /** 取消订单 */
    @PutMapping("/order/{orderNo}/cancel")
    public Result<Void> cancelOrder(@PathVariable String orderNo,
                                    @RequestHeader("X-User-Id") Long userId) {
        seckillService.cancelOrder(orderNo, userId);
        return Result.ok();
    }

    /** 退款 */
    @PutMapping("/order/{orderNo}/refund")
    public Result<Void> refundOrder(@PathVariable String orderNo) {
        seckillService.refundOrder(orderNo);
        return Result.ok();
    }
}
