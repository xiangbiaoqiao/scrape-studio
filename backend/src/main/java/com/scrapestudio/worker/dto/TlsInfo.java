package com.scrapestudio.worker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * TLS handshake details collected by the worker; {@code null} for plain HTTP targets.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TlsInfo(
        String protocol,
        String cipher,
        String alpn,
        Boolean authorized,
        String issuer,
        String validTo,
        String error
) {
}
