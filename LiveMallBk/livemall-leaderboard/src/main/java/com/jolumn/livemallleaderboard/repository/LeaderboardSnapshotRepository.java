package com.jolumn.livemallleaderboard.repository;

import com.jolumn.livemallleaderboard.entity.LeaderboardSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LeaderboardSnapshotRepository extends JpaRepository<LeaderboardSnapshot, Long> {

    List<LeaderboardSnapshot> findByActivityIdAndSnapshotTimeBetweenOrderByRankAsc(
            Long activityId, LocalDateTime start, LocalDateTime end);
}
