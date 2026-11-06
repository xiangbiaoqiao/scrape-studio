package com.scrapestudio.auth.dto;

import com.scrapestudio.user.User;

import java.time.Instant;

public record UserDto(
        Long id,
        String email,
        String displayName,
        String role,
        Instant createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getCreatedAt()
        );
    }
}
