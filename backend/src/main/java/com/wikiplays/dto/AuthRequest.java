package com.wikiplays.dto;

public record AuthRequest(
    String email,
    String password,
    String displayName,
    String legacyPlayerId
) {}
