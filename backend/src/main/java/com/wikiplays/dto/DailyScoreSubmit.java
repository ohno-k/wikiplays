package com.wikiplays.dto;

public record DailyScoreSubmit(
    Long dailyChallengeId,
    String playerId,
    String displayName,
    int score
) {}
