package com.scrapestudio.auth.dto;

public record AuthResponse(String token, UserDto user) {
}
