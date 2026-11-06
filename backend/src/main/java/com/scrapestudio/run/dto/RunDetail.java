package com.scrapestudio.run.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RunDetail(
        Long id,
        Long projectId,
        String projectName,
        String status,
        String triggerType,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        int itemCount,
        String errorMessage,
        List<Map<String, Object>> rows,
        Map<String, Object> diagnosis,
        String screenshotUrl
) {
}
