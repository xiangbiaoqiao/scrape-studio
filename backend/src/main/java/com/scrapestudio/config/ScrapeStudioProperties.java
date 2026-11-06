package com.scrapestudio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "scrapestudio")
public record ScrapeStudioProperties(
        Worker worker,
        Cors cors,
        Jwt jwt
) {
    public record Worker(
            String baseUrl,
            String token,
            int connectTimeoutMs,
            int readTimeoutMs
    ) {
    }

    public record Cors(String allowedOrigins) {
        public List<String> origins() {
            if (allowedOrigins == null || allowedOrigins.isBlank()) {
                return List.of("http://localhost:5173");
            }
            return Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
    }

    public record Jwt(String secret, long expirationHours) {
    }
}
