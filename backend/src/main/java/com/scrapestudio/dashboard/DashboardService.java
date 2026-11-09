package com.scrapestudio.dashboard;

import com.scrapestudio.dashboard.dto.DashboardSummary;
import com.scrapestudio.project.ProjectService;
import com.scrapestudio.run.Run;
import com.scrapestudio.run.RunRepository;
import com.scrapestudio.run.dto.RunSummary;
import com.scrapestudio.user.User;
import com.scrapestudio.worker.WorkerClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final RunRepository runRepository;
    private final ProjectService projectService;
    private final WorkerClient workerClient;

    public DashboardService(
            RunRepository runRepository,
            ProjectService projectService,
            WorkerClient workerClient
    ) {
        this.runRepository = runRepository;
        this.projectService = projectService;
        this.workerClient = workerClient;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary(User owner) {
        long runCount = runRepository.countByProjectOwnerId(owner.getId());
        long successCount = runRepository.countByProjectOwnerIdAndStatus(owner.getId(), Run.RunStatus.SUCCESS);
        long failedCount = runRepository.countByProjectOwnerIdAndStatus(owner.getId(), Run.RunStatus.FAILED);
        long blockedCount = runRepository.countByProjectOwnerIdAndStatus(owner.getId(), Run.RunStatus.BLOCKED);

        ZoneId zone = ZoneId.systemDefault();
        Instant startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant();

        long runsToday = runRepository.countByProjectOwnerIdAndStartedAtAfter(owner.getId(), startOfToday);
        long itemsCollected = runRepository.sumItemCountByOwnerId(owner.getId());
        double successRate = runCount == 0
                ? 0
                : Math.round((successCount * 1000.0) / runCount) / 10.0;

        List<RunSummary> recentRunList = runRepository
                .findTop8ByProjectOwnerIdOrderByStartedAtDesc(owner.getId())
                .stream()
                .map(this::toSummary)
                .toList();

        Instant windowStart = LocalDate.now(zone).minusDays(13).atStartOfDay(zone).toInstant();
        List<Run> windowRunList = runRepository.findByProjectOwnerIdAndStartedAtAfter(owner.getId(), windowStart);

        Map<LocalDate, long[]> bucketMap = new LinkedHashMap<>();
        for (int offset = 13; offset >= 0; offset--) {
            bucketMap.put(LocalDate.now(zone).minusDays(offset), new long[]{0, 0});
        }
        for (Run run : windowRunList) {
            LocalDate date = run.getStartedAt().atZone(zone).toLocalDate();
            long[] bucketArr = bucketMap.get(date);
            if (bucketArr != null) {
                bucketArr[0]++;
                if (run.getStatus() == Run.RunStatus.FAILED || run.getStatus() == Run.RunStatus.BLOCKED) {
                    bucketArr[1]++;
                }
            }
        }

        List<DashboardSummary.DailyActivity> activityList = bucketMap.entrySet().stream()
                .map(bucketEntry -> new DashboardSummary.DailyActivity(
                        bucketEntry.getKey().toString(),
                        bucketEntry.getValue()[0],
                        bucketEntry.getValue()[1]
                ))
                .toList();

        return new DashboardSummary(
                projectService.list(owner, null, 0, 1).total(),
                runCount,
                successCount,
                failedCount,
                blockedCount,
                runsToday,
                successRate,
                itemsCollected,
                workerClient.isHealthy(),
                recentRunList,
                activityList,
                projectService.recent(owner, 4)
        );
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
}
