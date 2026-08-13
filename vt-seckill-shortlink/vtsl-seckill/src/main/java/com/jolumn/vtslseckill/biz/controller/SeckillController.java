package com.jolumn.vtslseckill.biz.controller;

import com.jolumn.vtslcommon.dto.Result;
import com.jolumn.vtslcommon.util.SnowflakeIdGenerator;
import com.jolumn.vtslseckill.model.dto.CreateActivityRequest;
import com.jolumn.vtslseckill.model.entity.SeckillActivity;
import com.jolumn.vtslseckill.model.entity.SeckillOrder;
import com.jolumn.vtslseckill.biz.service.SeckillService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/seckill")
public class SeckillController {

    private final SeckillService seckillService;
    private final SnowflakeIdGenerator idGenerator;

    public SeckillController(SeckillService seckillService,
                             SnowflakeIdGenerator idGenerator) {
        this.seckillService = seckillService;
        this.idGenerator = idGenerator;
    }

    @PostMapping("/activity")
    public Result<SeckillActivity> createActivity(@Valid @RequestBody CreateActivityRequest request) {
        return Result.ok(seckillService.createActivity(request.toEntity()));
    }

    @PutMapping("/activity/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        seckillService.updateStatus(id, body.get("status"));
        return Result.ok();
    }

    /**
     * 获取秒杀活动详情
     * @param id
     * @return
     */
    @GetMapping("/activity/{id}")
    public Result<SeckillActivity> activityDetail(@PathVariable Long id) {
        return Result.ok(seckillService.getActivityCached(id));
    }

    @GetMapping("/activity/list")
    public Result<List<SeckillActivity>> activityList(@RequestParam(required = false) Long roomId) {
        return Result.ok(seckillService.getActivities(roomId));
    }

    @PostMapping("/order")
    public Result<Map<String, Object>> placeOrder(@RequestBody Map<String, Long> body,
                                                  @RequestHeader("X-User-Id") Long userId) {
        Long activityId = body.get("activityId");
        String orderNo = idGenerator.nextOrderNo();
        String result = seckillService.placeOrder(activityId, userId, orderNo);
        return Result.ok(Map.of("result", result, "orderNo", orderNo));
    }

    @GetMapping("/order/list")
    public Result<List<SeckillOrder>> orderList(@RequestHeader("X-User-Id") Long userId) {
        return Result.ok(seckillService.getUserOrders(userId));
    }

    @GetMapping("/order/{orderNo}")
    public Result<SeckillOrder> orderDetail(@PathVariable String orderNo) {
        return Result.ok(seckillService.getOrder(orderNo));
    }

    @PutMapping("/order/{orderNo}/cancel")
    public Result<Void> cancelOrder(@PathVariable String orderNo,
                                    @RequestHeader("X-User-Id") Long userId) {
        seckillService.cancelOrder(orderNo, userId);
        return Result.ok();
    }

    @PutMapping("/order/{orderNo}/refund")
    public Result<Void> refundOrder(@PathVariable String orderNo) {
        seckillService.refundOrder(orderNo);
        return Result.ok();
    }
}
