package com.jolumn.livemallseckill.repository;

import com.jolumn.livemallseckill.entity.SeckillActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeckillActivityRepository extends JpaRepository<SeckillActivity, Long> {

    List<SeckillActivity> findByStatusOrderByStartTimeAsc(Integer status);

    List<SeckillActivity> findByStatusAndEndTimeBefore(Integer status, java.time.LocalDateTime time);
}
