package com.wikiplays.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyChallengeResponse(
    Long id,
    LocalDate date,
    String scope,
    String genre,
    List<ArticleData> articles,
    long playerCount,
    int topScore
) {}
