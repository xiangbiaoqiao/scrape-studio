package com.scrapestudio.worker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Error envelope returned by the worker on non-2xx responses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkerError(String error) {
}
