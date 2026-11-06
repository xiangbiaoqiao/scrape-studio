package com.scrapestudio.worker.dto;

import java.util.List;

public record RenderResponse(
        String sessionId,
        String screenshotUrl,
        int width,
        int height,
        String title,
        String finalUrl,
        int status,
        boolean blocked,
        String blockReason
) {
    public static RenderResponse unavailable(String message) {
        return new RenderResponse(null, null, 0, 0, null, null, 0, false, message);
    }

    public boolean available() {
        return sessionId != null;
    }

    public List<String> warnings() {
        return blockReason == null ? List.of() : List.of(blockReason);
    }
}
