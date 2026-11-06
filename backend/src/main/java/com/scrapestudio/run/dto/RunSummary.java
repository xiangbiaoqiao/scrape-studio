package com.scrapestudio.run.dto;

import java.time.Instant;

public record RunSummary(
        Long id,
        Long projectId,
        String projectName,
        String status,
        String triggerType,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        int itemCount,
        String errorMessage
) {
}
