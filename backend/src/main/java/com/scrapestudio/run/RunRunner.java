package com.scrapestudio.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapestudio.project.Field;
import com.scrapestudio.project.FieldRepository;
import com.scrapestudio.project.Project;
import com.scrapestudio.worker.WorkerClient;
import com.scrapestudio.worker.dto.PreviewRequest;
import com.scrapestudio.worker.dto.PreviewResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Executes a queued run outside of the request thread.
 *
 * <p>The worker call can take tens of seconds, so no database transaction is held
 * while Playwright is working. The run row is loaded, executed, and saved back.</p>
 */
@Component
public class RunRunner {

    private static final Logger log = LoggerFactory.getLogger(RunRunner.class);

    private final RunRepository runRepository;
    private final FieldRepository fieldRepository;
    private final WorkerClient workerClient;
    private final ObjectMapper objectMapper;

    public RunRunner(
            RunRepository runRepository,
            FieldRepository fieldRepository,
            WorkerClient workerClient,
            ObjectMapper objectMapper
    ) {
        this.runRepository = runRepository;
        this.fieldRepository = fieldRepository;
        this.workerClient = workerClient;
        this.objectMapper = objectMapper;
    }

    public void execute(Long runId) {
        Run run = runRepository.findWithProjectById(runId).orElse(null);
        if (run == null) {
            log.warn("Run {} disappeared before it could execute", runId);
            return;
        }

        long started = System.currentTimeMillis();
        try {
            Project project = run.getProject();
            List<Field> fieldList = fieldRepository.findByProjectIdOrderByOrderIndexAscIdAsc(project.getId());

            List<PreviewRequest.PreviewField> fieldSpecList = fieldList.stream()
                    .map(field -> new PreviewRequest.PreviewField(
                            field.getName(),
                            field.getSelector(),
                            field.getSelectorType().name(),
                            field.getAttribute(),
                            field.isMultiple()
                    ))
                    .toList();

            PreviewResponse preview = workerClient.preview(new PreviewRequest(
                    project.getUrl(),
                    project.getItemSelector(),
                    project.getItemSelectorType().name(),
                    fieldSpecList,
                    200
            ));

            run.setItemCount(preview.count());
            run.setResultJson(objectMapper.writeValueAsString(preview.rows()));

            if (preview.blocked()) {
                run.setStatus(Run.RunStatus.BLOCKED);
                run.setErrorMessage(preview.blockReason());
            } else {
                run.setStatus(Run.RunStatus.SUCCESS);
            }
        } catch (Exception exception) {
            log.warn("Run {} failed: {}", runId, exception.getMessage());
            run.setStatus(Run.RunStatus.FAILED);
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            run.setErrorMessage(message.length() > 1900 ? message.substring(0, 1900) : message);
        } finally {
            run.setFinishedAt(Instant.now());
            run.setDurationMs(System.currentTimeMillis() - started);
            try {
                runRepository.save(run);
            } catch (Exception saveFailure) {
                log.error("Could not persist run {}", runId, saveFailure);
            }
        }
    }
}
