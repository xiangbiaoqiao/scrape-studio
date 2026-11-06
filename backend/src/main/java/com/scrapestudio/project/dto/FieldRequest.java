package com.scrapestudio.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FieldRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 1000) String selector,
        String selectorType,
        @Size(max = 80) String attribute,
        boolean multiple
) {
}
