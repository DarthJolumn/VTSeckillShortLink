package com.jolumn.livemallseckill.repository;

import com.jolumn.livemallseckill.entity.SeckillOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeckillOrderRepository extends JpaRepository<SeckillOrder, Long> {

    Optional<SeckillOrder> findByOrderNo(String orderNo);

    List<SeckillOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<SeckillOrder> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(Integer status, LocalDateTime time);

    List<SeckillOrder> findByStatusAndCancelledAtAfter(Integer status, LocalDateTime time);
}
