package com.scrapestudio.project.dto;

import java.time.Instant;
import java.util.List;

public record ProjectDetail(
        Long id,
        String name,
        String url,
        String description,
        String itemSelector,
        String itemSelectorType,
        boolean scheduleEnabled,
        int intervalMinutes,
        Instant lastRunAt,
        Instant nextRunAt,
        List<FieldDto> fields,
        Instant createdAt,
        Instant updatedAt
) {
}
