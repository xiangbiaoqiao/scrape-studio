package com.scrapestudio.run;

import com.scrapestudio.project.Project;
import com.scrapestudio.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class RunScheduler {

    private static final Logger log = LoggerFactory.getLogger(RunScheduler.class);

    private final ProjectRepository projectRepository;
    private final RunService runService;

    public RunScheduler(ProjectRepository projectRepository, RunService runService) {
        this.projectRepository = projectRepository;
        this.runService = runService;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void dispatchDueProjects() {
        List<Project> dueProjectList = projectRepository.findByScheduleEnabledTrueAndNextRunAtBefore(Instant.now());
        for (Project project : dueProjectList) {
            try {
                runService.triggerScheduled(project.getId());
            } catch (Exception exception) {
                log.warn("Could not schedule project {}: {}", project.getId(), exception.getMessage());
            }
        }
    }
}
