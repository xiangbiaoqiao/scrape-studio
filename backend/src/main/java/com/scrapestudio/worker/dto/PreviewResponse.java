package com.scrapestudio.worker.dto;

import java.util.List;
import java.util.Map;

public record PreviewResponse(
        List<Map<String, String>> rows,
        int count,
        String pageTitle,
        boolean blocked,
        String blockReason,
        List<String> warnings
) {
    public static PreviewResponse empty(String message) {
        return new PreviewResponse(List.of(), 0, null, false, message, List.of());
    }
}
