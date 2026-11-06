package com.scrapestudio.dashboard.dto;

import com.scrapestudio.project.dto.ProjectSummary;
import com.scrapestudio.run.dto.RunSummary;

import java.util.List;

public record DashboardSummary(
        long projectCount,
        long runCount,
        long successCount,
        long failedCount,
        long blockedCount,
        long runsToday,
        double successRate,
        long itemsCollected,
        boolean workerOnline,
        List<RunSummary> recentRuns,
        List<DailyActivity> activity,
        List<ProjectSummary> recentProjects
) {
    public record DailyActivity(String date, long total, long failed) {
    }
}
