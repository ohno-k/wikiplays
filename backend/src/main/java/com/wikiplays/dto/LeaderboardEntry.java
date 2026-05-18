package com.wikiplays.dto;

public record LeaderboardEntry(
    int rank,
    String displayName,
    long totalScore,
    int bestScore,
    long playCount
) {}
