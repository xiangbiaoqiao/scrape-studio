package com.scrapestudio.worker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Structured diagnosis returned by the Playwright worker.
 *
 * <p>Kept as a typed boundary object so controllers and run history never pass
 * raw {@code Map<String, Object>} payloads around. Unknown fields are ignored so
 * a newer worker can add signals without breaking the API.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiagnosisReport(
        String target,
        String finalUrl,
        String checkedAt,
        Long durationMs,
        Integer httpStatus,
        String verdict,
        Integer riskScore,
        String summary,
        String server,
        TlsInfo tls,
        List<DiagnosisSignal> signals,
        List<String> recommendations
) {
}
