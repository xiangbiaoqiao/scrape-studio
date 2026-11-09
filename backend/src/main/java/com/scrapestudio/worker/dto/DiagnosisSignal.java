package com.scrapestudio.worker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One diagnostic signal (TLS, HTTP/2, CDN, challenge page, robots.txt, ...).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiagnosisSignal(
        String name,
        String status,
        String detail
) {
}
