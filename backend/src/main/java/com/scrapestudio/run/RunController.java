package com.scrapestudio.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapestudio.common.PageResponse;
import com.scrapestudio.run.dto.RunDetail;
import com.scrapestudio.run.dto.RunSummary;
import com.scrapestudio.user.User;
import com.scrapestudio.worker.WorkerClient;
import com.scrapestudio.worker.dto.DiagnosisReport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class RunController {

    private static final DateTimeFormatter FILE_STAMP;

    private final RunService runService;
    private final WorkerClient workerClient;
    private final ObjectMapper objectMapper;

    public RunController(RunService runService, WorkerClient workerClient, ObjectMapper objectMapper) {
        this.runService = runService;
        this.workerClient = workerClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/projects/{projectId}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunDetail trigger(
            @AuthenticationPrincipal User user,
            @PathVariable Long projectId
    ) {
        return runService.trigger(user, projectId, Run.RunTrigger.MANUAL);
    }

    @GetMapping("/projects/{projectId}/runs")
    public PageResponse<RunSummary> listForProject(
            @AuthenticationPrincipal User user,
            @PathVariable Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return runService.list(user, projectId, status, page, size);
    }

    @GetMapping("/runs")
    public PageResponse<RunSummary> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return runService.list(user, projectId, status, page, size);
    }

    @GetMapping("/runs/{id}")
    public RunDetail get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return runService.get(user, id);
    }

    @DeleteMapping("/runs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        runService.delete(user, id);
    }

    @PostMapping("/runs/{id}/diagnose")
    public DiagnosisReport diagnose(@AuthenticationPrincipal User user, @PathVariable Long id) {
        Run run = runService.requireRun(user, id);
        DiagnosisReport diagnosisReport = workerClient.diagnose(run.getProject().getUrl());
        runService.saveDiagnosis(run.getId(), diagnosisReport);
        return diagnosisReport;
    }

    @GetMapping("/runs/{id}/export")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam(defaultValue = "csv") String format
    ) {
        Run run = runService.requireRun(user, id);
        List<Map<String, Object>> rowList = runService.rows(run);
        String baseName = sanitize(run.getProject().getName())
                + "-" + run.getStatus().name().toLowerCase()
                + "-" + FILE_STAMP.format(run.getStartedAt());

        if ("json".equalsIgnoreCase(format)) {
            String json = run.getResultJson() == null || run.getResultJson().isBlank()
                    ? "[]"
                    : run.getResultJson();
            return attachment(baseName + ".json", MediaType.APPLICATION_JSON, json);
        }
        if (!"csv".equalsIgnoreCase(format)) {
            throw new IllegalArgumentException("format must be csv or json");
        }

        String csv = toCsv(rowList);
        return attachment(baseName + ".csv", new MediaType("text", "csv", StandardCharsets.UTF_8), csv);
    }

    private ResponseEntity<byte[]> attachment(String filename, MediaType mediaType, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String disposition = "attachment; filename=\"" + filename + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(mediaType)
                .body(bytes);
    }

    private String toCsv(List<Map<String, Object>> rowList) {
        Set<String> columns = new LinkedHashSet<>();
        for (Map<String, Object> rowMap : rowList) {
            columns.addAll(rowMap.keySet());
        }
        if (columns.isEmpty()) {
            return "";
        }

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(String.join(",", columns.stream().map(this::escape).toList())).append("\r\n");

        for (Map<String, Object> rowMap : rowList) {
            csv.append(columns.stream()
                    .map(column -> escape(cell(rowMap.get(column))))
                    .reduce((left, right) -> left + "," + right)
                    .orElse(""));
            csv.append("\r\n");
        }
        return csv.toString();
    }

    private String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> || value instanceof Map<?, ?>) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception exception) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    private String escape(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private String sanitize(String value) {
        String cleaned = value == null ? "export" : value.trim().replaceAll("[^\\p{L}\\p{N}._-]+", "-");
        cleaned = cleaned.replaceAll("^-+|-+$", "");
        return cleaned.isEmpty() ? "export" : cleaned;
    }

    static {
        FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    }
}
