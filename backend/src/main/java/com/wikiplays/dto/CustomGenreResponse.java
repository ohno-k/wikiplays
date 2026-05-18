package com.wikiplays.dto;

import java.time.Instant;
import java.util.List;

public record CustomGenreResponse(
    Long id,
    String name,
    String emoji,
    List<String> categories,
    String creatorName,
    Instant createdAt,
    long playCount,
    boolean mine
) {}
