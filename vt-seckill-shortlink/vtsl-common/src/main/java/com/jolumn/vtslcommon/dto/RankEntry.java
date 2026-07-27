package com.jolumn.vtslcommon.dto;

import java.io.Serializable;

/** 排行榜条目 */
public class RankEntry implements Serializable {

    private Long userId;
    private Double score;
    private Integer rank;

    public RankEntry() {}

    public RankEntry(Long userId, Double score, Integer rank) {
        this.userId = userId;
        this.score = score;
        this.rank = rank;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
}
