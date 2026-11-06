package com.scrapestudio.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 2048) String url,
        @Size(max = 1000) String description
) {
}
