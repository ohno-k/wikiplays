package com.wikiplays.dto;

public record AuthResponse(
    String token,
    UserInfo user
) {
    public record UserInfo(
        Long id,
        String email,
        String displayName,
        String role,
        String plan,
        boolean premiumActive
    ) {}
}
