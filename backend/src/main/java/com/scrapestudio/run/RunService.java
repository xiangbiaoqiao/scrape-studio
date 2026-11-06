package com.scrapestudio.run;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapestudio.common.ApiException;
import com.scrapestudio.common.PageResponse;
import com.scrapestudio.project.Project;
import com.scrapestudio.project.ProjectRepository;
import com.scrapestudio.project.ProjectService;
import com.scrapestudio.run.dto.RunDetail;
import com.scrapestudio.run.dto.RunSummary;
import com.scrapestudio.user.User;
import com.scrapestudio.worker.WorkerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

@Service
public class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);

    private final RunRepository runRepository;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final RunRunner runRunner;
    private final WorkerClient workerClient;
    private final ObjectMapper objectMapper;
    private final Executor runExecutor;

    public RunService(
            RunRepository runRepository,
            ProjectRepository projectRepository,
            ProjectService projectService,
            RunRunner runRunner,
            WorkerClient workerClient,
            ObjectMapper objectMapper,
            @Qualifier("runExecutor") Executor runExecutor
    ) {
        this.runRepository = runRepository;
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.runRunner = runRunner;
        this.workerClient = workerClient;
        this.objectMapper = objectMapper;
        this.runExecutor = runExecutor;
    }

    @Transactional
    public RunDetail trigger(User owner, Long projectId, Run.RunTrigger triggerType) {
        Project project = projectService.requireProject(owner, projectId);
        return createRun(project, triggerType);
    }

    /**
     * Called by the scheduler. Guards against overlapping runs and advances
     * {@code nextRunAt} before the run starts.
     */
    @Transactional
    public void triggerScheduled(Long projectId) {
        Optional<Project> maybeProject = projectRepository.findById(projectId);
        if (maybeProject.isEmpty()) {
            return;
        }
        Project project = maybeProject.get();
        if (!project.isScheduleEnabled()) {
            return;
        }

        Optional<Run> lastRun = runRepository.findFirstByProjectIdOrderByStartedAtDesc(projectId);
        if (lastRun.isPresent() && lastRun.get().getStatus() == Run.RunStatus.RUNNING) {
            project.setNextRunAt(Instant.now().plus(project.getIntervalMinutes(), ChronoUnit.MINUTES));
            projectRepository.save(project);
            return;
        }

        createRun(project, Run.RunTrigger.SCHEDULE);
    }

    @Transactional(readOnly = true)
    public PageResponse<RunSummary> list(
            User owner,
            Long projectId,
            String status,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "startedAt")
        );

        Run.RunStatus parsedStatus = parseStatus(status);
        Page<Run> runs;

        if (projectId != null && parsedStatus != null) {
            runs = runRepository.findByProjectOwnerIdAndProjectIdAndStatusOrderByStartedAtDesc(
                    owner.getId(), projectId, parsedStatus, pageable);
        } else if (projectId != null) {
            runs = runRepository.findByProjectOwnerIdAndProjectIdOrderByStartedAtDesc(
                    owner.getId(), projectId, pageable);
        } else if (parsedStatus != null) {
            runs = runRepository.findByProjectOwnerIdAndStatusOrderByStartedAtDesc(
                    owner.getId(), parsedStatus, pageable);
        } else {
            runs = runRepository.findByProjectOwnerIdOrderByStartedAtDesc(owner.getId(), pageable);
        }

        return PageResponse.of(runs.map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public RunDetail get(User owner, Long id) {
        return toDetail(requireRun(owner, id));
    }

    @Transactional(readOnly = true)
    public Run requireRun(User owner, Long id) {
        return runRepository.findByIdAndProjectOwnerId(id, owner.getId())
                .orElseThrow(() -> ApiException.notFound("Run not found"));
    }

    @Transactional
    public void saveDiagnosis(Long runId, Map<String, Object> report) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> ApiException.notFound("Run not found"));
        try {
            run.setDiagnosisJson(objectMapper.writeValueAsString(report));
        } catch (Exception exception) {
            log.warn("Could not serialize diagnosis for run {}", runId, exception);
        }
        runRepository.save(run);
    }

    @Transactional
    public void delete(User owner, Long id) {
        Run run = requireRun(owner, id);
        runRepository.delete(run);
    }

    public List<Map<String, Object>> rows(Run run) {
        if (run.getResultJson() == null || run.getResultJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(run.getResultJson(), new TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private RunDetail createRun(Project project, Run.RunTrigger triggerType) {
        Run run = new Run();
        run.setProject(project);
        run.setStatus(Run.RunStatus.RUNNING);
        run.setTriggerType(triggerType);
        run.setStartedAt(Instant.now());
        run = runRepository.saveAndFlush(run);

        project.setLastRunAt(Instant.now());
        if (project.isScheduleEnabled()) {
            project.setNextRunAt(Instant.now().plus(project.getIntervalMinutes(), ChronoUnit.MINUTES));
        }
        projectRepository.save(project);

        Long runId = run.getId();
        dispatchAfterCommit(runId);
        return toDetail(run);
    }

    private void dispatchAfterCommit(Long runId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runExecutor.execute(() -> runRunner.execute(runId));
                }
            });
        } else {
            runExecutor.execute(() -> runRunner.execute(runId));
        }
    }

    private RunSummary toSummary(Run run) {
        return new RunSummary(
                run.getId(),
                run.getProject().getId(),
                run.getProject().getName(),
                run.getStatus().name(),
                run.getTriggerType().name(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                run.getItemCount(),
                run.getErrorMessage()
        );
    }

    private RunDetail toDetail(Run run) {
        return new RunDetail(
                run.getId(),
                run.getProject().getId(),
                run.getProject().getName(),
                run.getStatus().name(),
                run.getTriggerType().name(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                run.getItemCount(),
                run.getErrorMessage(),
                rows(run),
                parseDiagnosis(run.getDiagnosisJson()),
                run.getScreenshotUrl()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDiagnosis(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private Run.RunStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return Run.RunStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("status must be RUNNING, SUCCESS, FAILED or BLOCKED");
        }
    }
}
