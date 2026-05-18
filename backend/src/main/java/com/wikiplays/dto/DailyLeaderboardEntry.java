package com.wikiplays.dto;

import java.time.Instant;

public record DailyLeaderboardEntry(
    String displayName,
    int score,
    Instant playedAt,
    int rank
) {}
