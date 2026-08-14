package com.jolumn.vtslseckill.biz.repository;

import com.jolumn.vtslseckill.model.entity.SeckillOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeckillOrderRepository extends JpaRepository<SeckillOrder, Long> {

    Optional<SeckillOrder> findByOrderNo(String orderNo);

    List<SeckillOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<SeckillOrder> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(Integer status, LocalDateTime time);

    List<SeckillOrder> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(Integer status, LocalDateTime time, Pageable pageable);

    List<SeckillOrder> findByStatusAndCancelledAtAfter(Integer status, LocalDateTime time);

    List<SeckillOrder> findByStatusAndCancelledAtAfter(Integer status, LocalDateTime time, Pageable pageable);

    /** 丢单对账用：判断某用户在某活动是否已有订单 */
    boolean existsByActivityIdAndUserId(Long activityId, Long userId);
}
