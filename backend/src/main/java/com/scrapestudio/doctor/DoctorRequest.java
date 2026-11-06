package com.scrapestudio.doctor;

import jakarta.validation.constraints.NotBlank;

public record DoctorRequest(@NotBlank String url) {
}
