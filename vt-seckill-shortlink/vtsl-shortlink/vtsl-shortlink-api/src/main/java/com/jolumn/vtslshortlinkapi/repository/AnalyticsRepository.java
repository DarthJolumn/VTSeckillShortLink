package com.jolumn.vtslshortlinkapi.repository;

import com.jolumn.vtslshortlinkapi.entity.Analytics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalyticsRepository extends JpaRepository<Analytics, Long> {

    List<Analytics> findByUrlIdOrderByClickedAtDesc(Long urlId);
}
