package com.jolumn.vtslleaderboard.service;

import com.jolumn.vtslcommon.dto.RankEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaderboardServiceImplTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ZSetOperations<String, String> zSetOps;

    private LeaderboardServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        service = new LeaderboardServiceImpl(redisTemplate);
    }

    @Test
    void addScore_shouldIncrementScore() {
        when(zSetOps.incrementScore(anyString(), anyString(), anyDouble())).thenReturn(5.0);

        boolean result = service.addScore(1L, 100L, "GIFT");
        assertThat(result).isTrue();
        verify(zSetOps).incrementScore("leaderboard:1", "100", 5.0);
    }

    @Test
    void getTopN_shouldReturnRankedList() {
        ZSetOperations.TypedTuple<String> t1 = mock(ZSetOperations.TypedTuple.class);
        when(t1.getValue()).thenReturn("100");
        when(t1.getScore()).thenReturn(99.0);

        HashSet<ZSetOperations.TypedTuple<String>> set = new HashSet<>();
        set.add(t1);
        when(zSetOps.reverseRangeWithScores("leaderboard:1", 0, 9)).thenReturn(set);

        List<RankEntry> result = service.getTopN(1L, 10);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(100L);
        assertThat(result.get(0).getScore()).isEqualTo(99.0);
        assertThat(result.get(0).getRank()).isEqualTo(1);
    }

    @Test
    void getRank_userFound() {
        when(zSetOps.score("leaderboard:1", "100")).thenReturn(99.0);
        when(zSetOps.reverseRank("leaderboard:1", "100")).thenReturn(2L);

        RankEntry result = service.getRank(1L, 100L);
        assertThat(result.getUserId()).isEqualTo(100L);
        assertThat(result.getScore()).isEqualTo(99.0);
        assertThat(result.getRank()).isEqualTo(3); // reverseRank 0-based + 1
    }

    @Test
    void getRank_userNotFound() {
        when(zSetOps.score("leaderboard:1", "999")).thenReturn(null);

        RankEntry result = service.getRank(1L, 999L);
        assertThat(result.getRank()).isEqualTo(-1);
    }
}
