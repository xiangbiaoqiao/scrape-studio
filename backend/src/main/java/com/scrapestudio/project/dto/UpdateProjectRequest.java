package com.scrapestudio.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 2048) String url,
        @Size(max = 1000) String description,
        @Size(max = 1000) String itemSelector,
        String itemSelectorType,
        Boolean scheduleEnabled,
        @Min(1) @Max(10080) Integer intervalMinutes
) {
}
