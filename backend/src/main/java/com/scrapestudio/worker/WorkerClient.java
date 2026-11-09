package com.scrapestudio.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapestudio.common.ApiException;
import com.scrapestudio.config.ScrapeStudioProperties;
import com.scrapestudio.worker.dto.DiagnosisReport;
import com.scrapestudio.worker.dto.PickResponse;
import com.scrapestudio.worker.dto.PreviewRequest;
import com.scrapestudio.worker.dto.PreviewResponse;
import com.scrapestudio.worker.dto.RenderResponse;
import com.scrapestudio.worker.dto.WorkerError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

@Component
public class WorkerClient {

    private static final Logger log = LoggerFactory.getLogger(WorkerClient.class);

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public WorkerClient(ScrapeStudioProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.worker().connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.worker().readTimeoutMs()));

        this.client = RestClient.builder()
                .baseUrl(properties.worker().baseUrl())
                .defaultHeader("X-Worker-Token", properties.worker().token())
                .requestFactory(requestFactory)
                .build();
    }

    public RenderResponse render(String url) {
        return post("/api/render", Map.of("url", url), RenderResponse.class);
    }

    public PickResponse pick(String sessionId, double x, double y) {
        return post("/api/pick", Map.of("sessionId", sessionId, "x", x, "y", y), PickResponse.class);
    }

    public PreviewResponse preview(PreviewRequest request) {
        return post("/api/preview", request, PreviewResponse.class);
    }

    public DiagnosisReport diagnose(String url) {
        return post("/api/diagnose", Map.of("url", url), DiagnosisReport.class);
    }

    public boolean isHealthy() {
        try {
            client.get().uri("/api/health").retrieve().body(Map.class);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            return client.post()
                    .uri(path)
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException exception) {
            throw ApiException.badGateway(workerMessage(exception));
        } catch (Exception exception) {
            log.warn("Rendering worker unavailable: {}", exception.getMessage());
            throw ApiException.badGateway(
                    "Rendering service is unavailable. Start it with: cd worker && npm run dev"
            );
        }
    }

    private String workerMessage(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        try {
            WorkerError payload = objectMapper.readValue(body, WorkerError.class);
            if (payload.error() != null) {
                return "Rendering service: " + payload.error();
            }
        } catch (Exception parseException) {
            log.debug("Worker error response was not a JSON envelope: {}", parseException.getMessage());
        }
        String message = body == null ? "" : body.strip();
        if (message.length() > 300) {
            message = message.substring(0, 300);
        }
        return "Rendering service error: " + (message.isEmpty() ? exception.getStatusCode() : message);
    }
}
