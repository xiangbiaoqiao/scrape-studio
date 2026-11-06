package com.scrapestudio.project.dto;

import java.time.Instant;

public record ProjectSummary(
        Long id,
        String name,
        String url,
        String description,
        String itemSelector,
        boolean scheduleEnabled,
        int intervalMinutes,
        Instant lastRunAt,
        Instant nextRunAt,
        long fieldCount,
        long runCount,
        String lastStatus,
        Instant updatedAt
) {
}
