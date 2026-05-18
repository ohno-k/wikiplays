package com.wikiplays.dto;

public record PlayRecordSubmit(
    String mode,
    String genre,
    String scope,
    Long communityGenreId,
    int score,
    int maxScore,
    String difficulty,
    /** 匿名プレイ時の playerId (ログイン時は無視される)。 */
    String playerId
) {}
