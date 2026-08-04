package com.jolumn.vtslshortlink.repository;

import com.jolumn.vtslshortlink.entity.LinkClickStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface LinkClickStatsRepository extends JpaRepository<LinkClickStats, Long> {

    @Modifying
    @Query(value = "INSERT INTO t_link_click_stats (short_code, click_date, click_count) " +
                   "VALUES (:shortCode, :clickDate, 1) " +
                   "ON DUPLICATE KEY UPDATE click_count = click_count + 1",
           nativeQuery = true)
    void incrementClickCount(@Param("shortCode") String shortCode,
                             @Param("clickDate") LocalDate clickDate);
}
