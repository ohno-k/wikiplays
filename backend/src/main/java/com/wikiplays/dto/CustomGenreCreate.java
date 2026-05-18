package com.wikiplays.dto;

import java.util.List;

public record CustomGenreCreate(
    String name,
    String emoji,
    List<String> categories,
    String creatorId,
    String creatorName
) {}
